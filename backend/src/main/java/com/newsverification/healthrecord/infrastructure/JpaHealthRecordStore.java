/* 저장 건강 분석 JPA Adapter */
package com.newsverification.healthrecord.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.healthrecord.application.HealthRecordStore;
import com.newsverification.publisher.domain.NewsPublisherDomain;
import com.newsverification.publisher.infrastructure.NewsPublisherDomainRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 건강 분석 Aggregate의 MySQL 저장과 목록 조회 */
@Component
public class JpaHealthRecordStore implements HealthRecordStore {

    private static final String SAVE_IDENTITY_CONSTRAINT =
            "uk_health_records_user_url_analyzed";

    private final HealthAnalysisRecordRepository recordRepository;
    private final HealthClaimRepository claimRepository;
    private final HealthEvidenceRepository evidenceRepository;
    private final HealthClaimEvidenceRepository relationRepository;
    private final NewsPublisherDomainRepository publisherDomainRepository;
    private final TransactionTemplate writeTransaction;

    public JpaHealthRecordStore(
            HealthAnalysisRecordRepository recordRepository,
            HealthClaimRepository claimRepository,
            HealthEvidenceRepository evidenceRepository,
            HealthClaimEvidenceRepository relationRepository,
            NewsPublisherDomainRepository publisherDomainRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.recordRepository = recordRepository;
        this.claimRepository = claimRepository;
        this.evidenceRepository = evidenceRepository;
        this.relationRepository = relationRepository;
        this.publisherDomainRepository = publisherDomainRepository;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 결과·주장·근거의 단일 Transaction 저장 */
    @Override
    public SavedRecord save(SaveCommand command) {
        HealthAnalysisResult result = command.result();
        byte[] articleDigest = digest(result.article().url().normalize().toASCIIString());
        var existing = findExisting(command, articleDigest);
        if (existing != null) {
            return existing;
        }
        try {
            return Objects.requireNonNull(
                    writeTransaction.execute(status -> saveNew(command, articleDigest))
            );
        } catch (DataIntegrityViolationException exception) {
            if (!isSaveIdentityViolation(exception)) {
                throw exception;
            }
            SavedRecord concurrentSave = findExisting(command, articleDigest);
            if (concurrentSave == null) {
                throw exception;
            }
            return concurrentSave;
        }
    }

    /** 회원별 최신 저장 기록 조회 */
    @Override
    @Transactional(readOnly = true)
    public PageResult findAll(long userId, java.time.Instant activeAt, int page, int size) {
        Page<HealthAnalysisRecordEntity> records =
                recordRepository.findByUserIdAndExpiresAtAfterOrderByAnalyzedAtDescIdDesc(
                        userId,
                        activeAt,
                        PageRequest.of(page, size)
                );
        return new PageResult(
                records.getContent().stream().map(JpaHealthRecordStore::toSavedRecord).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements(),
                records.getTotalPages(),
                records.hasNext()
        );
    }

    /** 기준 시각 이하 만료 기록 일괄 삭제 */
    @Override
    public int deleteExpiredAtOrBefore(java.time.Instant cutoff) {
        return recordRepository.deleteExpiredAtOrBefore(cutoff);
    }

    private SavedRecord saveNew(SaveCommand command, byte[] articleDigest) {
        HealthAnalysisResult result = command.result();
        NewsPublisherDomain publisherDomain = publisherDomainRepository
                .findByHostname(result.article().url().getHost())
                .orElseThrow(() -> new IllegalStateException(
                        "Publisher domain missing for completed analysis"));
        HealthAnalysisRecordEntity record = recordRepository.saveAndFlush(
                new HealthAnalysisRecordEntity(
                        command.userId(),
                        publisherDomain.id(),
                        result.article().url().toASCIIString(),
                        articleDigest,
                        result.article().title(),
                        toUtcDateTime(result.article().publishedAt()),
                        toUtcDateTime(result.article().modifiedAt()),
                        result.analyzedAt(),
                        command.expiresAt(),
                        result.overallStatus(),
                        result.totalClaimCount(),
                        result.confirmedClaimCount(),
                        result.confirmationRate(),
                        result.expertReviewStatus(),
                        result.aiModelVersion(),
                        result.policyVersion(),
                        result.evidenceAllowlistVersion()
                )
        );
        saveChildren(record.id(), result);
        return toSavedRecord(record);
    }

    private SavedRecord findExisting(SaveCommand command, byte[] articleDigest) {
        return recordRepository.findByUserIdAndNormalizedUrlDigestAndAnalyzedAt(
                        command.userId(), articleDigest, command.result().analyzedAt())
                .map(JpaHealthRecordStore::toSavedRecord)
                .orElse(null);
    }

    private static boolean isSaveIdentityViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraintName = constraintViolation.getConstraintName();
                if (constraintName == null) {
                    return false;
                }
                String unquotedName = constraintName.replace("`", "");
                int qualifierIndex = unquotedName.lastIndexOf('.');
                String simpleName = qualifierIndex < 0
                        ? unquotedName
                        : unquotedName.substring(qualifierIndex + 1);
                return SAVE_IDENTITY_CONSTRAINT.equalsIgnoreCase(simpleName);
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void saveChildren(long recordId, HealthAnalysisResult result) {
        Map<String, HealthEvidenceEntity> evidenceByUrl = new LinkedHashMap<>();
        for (HealthAnalysisResult.Claim claim : result.claims()) {
            HealthClaimEntity savedClaim = claimRepository.saveAndFlush(
                    new HealthClaimEntity(recordId, claim)
            );
            for (int index = 0; index < claim.evidences().size(); index++) {
                HealthAnalysisResult.Evidence evidence = claim.evidences().get(index);
                String sourceUrl = evidence.sourceUrl().normalize().toASCIIString();
                HealthEvidenceEntity savedEvidence = evidenceByUrl.get(sourceUrl);
                if (savedEvidence == null) {
                    savedEvidence = evidenceRepository.saveAndFlush(
                            new HealthEvidenceEntity(recordId, evidence, digest(sourceUrl))
                    );
                    evidenceByUrl.put(sourceUrl, savedEvidence);
                }
                relationRepository.save(new HealthClaimEvidenceEntity(
                        savedClaim.id(),
                        savedEvidence.id(),
                        index + 1,
                        evidence
                ));
            }
        }
        relationRepository.flush();
    }

    private static LocalDateTime toUtcDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static SavedRecord toSavedRecord(HealthAnalysisRecordEntity entity) {
        return new SavedRecord(
                entity.id(),
                entity.articleTitle(),
                entity.overallStatus().name(),
                entity.analyzedAt(),
                entity.expiresAt()
        );
    }
}
