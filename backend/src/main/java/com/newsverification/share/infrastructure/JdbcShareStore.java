/* 공유 결과 MySQL Adapter */
package com.newsverification.share.infrastructure;

import com.newsverification.share.application.ShareException;
import com.newsverification.share.application.ShareService;
import com.newsverification.share.application.ShareStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 기존 공유 Schema를 사용하는 Digest 기반 저장과 조회 */
@Component
public class JdbcShareStore implements ShareStore {

    private final JdbcTemplate jdbc;

    public JdbcShareStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActiveUser(long userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND status = 'ACTIVE'",
                Integer.class,
                userId
        );
        return count != null && count == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HealthSource> findHealthSource(long recordId, long userId, Instant activeAt) {
        List<HealthRow> rows = jdbc.query(
                """
                SELECT record.id, record.article_url, record.article_title, publisher.name,
                       record.article_published_at, record.article_modified_at, record.analyzed_at,
                       record.overall_status, record.verification_rate, record.supported_claim_count,
                       record.total_claim_count, record.expert_review_status
                FROM health_analysis_records record
                JOIN news_publisher_domains domain ON domain.id = record.publisher_domain_id
                JOIN news_publishers publisher ON publisher.id = domain.publisher_id
                WHERE record.id = ? AND record.user_id = ? AND record.expires_at > ?
                """,
                (result, rowNumber) -> healthRow(result),
                recordId,
                userId,
                Timestamp.from(activeAt)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        HealthRow row = rows.get(0);
        List<ShareService.Claim> claims = claims(recordId);
        boolean limitedEvidence = claims.stream().anyMatch(claim ->
                "NEEDS_REVIEW".equals(claim.status()) || "INSUFFICIENT".equals(claim.status()));
        return Optional.of(new HealthSource(
                row.id(), row.article(), row.analyzedAt(), row.overallStatus(), row.confirmationRate(),
                row.confirmedClaimCount(), row.totalClaimCount(), claims, row.expertReviewStatus(), limitedEvidence
        ));
    }

    @Override
    public void saveHealth(
            long recordId,
            byte[] tokenDigest,
            String shortSummary,
            Instant createdAt,
            Instant expiresAt
    ) {
        jdbc.update(
                """
                INSERT INTO health_share_links (
                    health_analysis_record_id, token_digest, short_summary, created_at, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                recordId, tokenDigest, shortSummary, Timestamp.from(createdAt), Timestamp.from(expiresAt)
        );
    }

    @Override
    @Transactional
    public void saveHeadline(
            long userId,
            ShareService.Article article,
            byte[] tokenDigest,
            String alternativeHeadline,
            String shortSummary,
            Instant analyzedAt,
            Instant createdAt,
            Instant expiresAt,
            String aiModelVersion,
            String policyVersion,
            List<ShareService.HeadlineIssue> issues
    ) {
        Long publisherDomainId = jdbc.query(
                """
                SELECT domain.id
                FROM news_publisher_domains domain
                JOIN news_publishers publisher ON publisher.id = domain.publisher_id
                WHERE domain.hostname = ? AND domain.status = 'ACTIVE' AND publisher.status = 'ACTIVE'
                """,
                result -> result.next() ? result.getLong(1) : null,
                java.net.URI.create(article.url()).getHost()
        );
        if (publisherDomainId == null) {
            throw new ShareException("SHARE_SOURCE_UNAVAILABLE");
        }
        byte[] articleDigest = digest(article.url());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO headline_share_records (
                        created_by_user_id, publisher_domain_id, token_digest, article_url,
                        normalized_url_digest, article_title, alternative_title, short_summary,
                        analyzed_at, created_at, expires_at, ai_model_version, policy_version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, userId);
            statement.setLong(2, publisherDomainId);
            statement.setBytes(3, tokenDigest);
            statement.setString(4, article.url());
            statement.setBytes(5, articleDigest);
            statement.setString(6, article.title());
            statement.setString(7, alternativeHeadline);
            statement.setString(8, shortSummary);
            statement.setTimestamp(9, Timestamp.from(analyzedAt));
            statement.setTimestamp(10, Timestamp.from(createdAt));
            statement.setTimestamp(11, Timestamp.from(expiresAt));
            statement.setString(12, aiModelVersion);
            statement.setString(13, policyVersion);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Headline share identifier was not generated");
        }
        for (int index = 0; index < issues.size(); index++) {
            ShareService.HeadlineIssue issue = issues.get(index);
            jdbc.update(
                    """
                    INSERT INTO headline_share_issues (
                        headline_share_record_id, issue_order, issue_type, reason
                    ) VALUES (?, ?, ?, ?)
                    """,
                    key.longValue(), index + 1, issue.type(), issue.explanation()
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShareService.HealthResult> findHealth(byte[] tokenDigest, Instant activeAt) {
        List<HealthShareRow> rows = jdbc.query(
                """
                SELECT link.expires_at, record.id, record.article_url, record.article_title, publisher.name,
                       record.article_published_at, record.article_modified_at, record.analyzed_at,
                       record.overall_status, record.verification_rate, record.supported_claim_count,
                       record.total_claim_count, record.expert_review_status
                FROM health_share_links link
                JOIN health_analysis_records record ON record.id = link.health_analysis_record_id
                JOIN news_publisher_domains domain ON domain.id = record.publisher_domain_id
                JOIN news_publishers publisher ON publisher.id = domain.publisher_id
                WHERE link.token_digest = ? AND link.revoked_at IS NULL
                  AND link.expires_at > ? AND record.expires_at > ?
                """,
                (result, rowNumber) -> new HealthShareRow(
                        result.getTimestamp("expires_at").toInstant(), healthRow(result)
                ),
                tokenDigest,
                Timestamp.from(activeAt),
                Timestamp.from(activeAt)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        HealthShareRow share = rows.get(0);
        HealthRow row = share.health();
        List<ShareService.Claim> claims = claims(row.id());
        boolean limitedEvidence = claims.stream().anyMatch(claim ->
                "NEEDS_REVIEW".equals(claim.status()) || "INSUFFICIENT".equals(claim.status()));
        return Optional.of(new ShareService.HealthResult(
                "HEALTH", share.expiresAt(), row.article(), row.analyzedAt(), row.overallStatus(),
                row.confirmationRate(), row.confirmedClaimCount(), row.totalClaimCount(), claims,
                row.expertReviewStatus(), limitedEvidence
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShareService.HeadlineResult> findHeadline(byte[] tokenDigest, Instant activeAt) {
        List<HeadlineShareRow> rows = jdbc.query(
                """
                SELECT share.id, share.expires_at, share.article_url, share.article_title,
                       publisher.name, share.analyzed_at, share.alternative_title
                FROM headline_share_records share
                JOIN news_publisher_domains domain ON domain.id = share.publisher_domain_id
                JOIN news_publishers publisher ON publisher.id = domain.publisher_id
                WHERE share.token_digest = ? AND share.revoked_at IS NULL AND share.expires_at > ?
                """,
                (result, rowNumber) -> new HeadlineShareRow(
                        result.getLong("id"),
                        result.getTimestamp("expires_at").toInstant(),
                        new ShareService.Article(
                                result.getString("article_url"), result.getString("article_title"),
                                result.getString("name"), null, null
                        ),
                        result.getTimestamp("analyzed_at").toInstant(),
                        result.getString("alternative_title")
                ),
                tokenDigest,
                Timestamp.from(activeAt)
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        HeadlineShareRow row = rows.get(0);
        List<ShareService.HeadlineIssue> issues = jdbc.query(
                """
                SELECT issue_type, reason FROM headline_share_issues
                WHERE headline_share_record_id = ? ORDER BY issue_order
                """,
                (result, rowNumber) -> new ShareService.HeadlineIssue(
                        result.getString("issue_type"), result.getString("reason")
                ),
                row.id()
        );
        return Optional.of(new ShareService.HeadlineResult(
                "HEADLINE", row.expiresAt(), row.article(), row.analyzedAt(), issues, row.alternativeHeadline()
        ));
    }

    @Override
    public boolean revokeHealth(byte[] tokenDigest, long userId, Instant revokedAt) {
        return jdbc.update(
                """
                UPDATE health_share_links link
                JOIN health_analysis_records record ON record.id = link.health_analysis_record_id
                SET link.revoked_at = ?
                WHERE link.token_digest = ? AND record.user_id = ? AND link.revoked_at IS NULL
                """,
                Timestamp.from(revokedAt), tokenDigest, userId
        ) == 1;
    }

    @Override
    public boolean deleteHeadline(byte[] tokenDigest, long userId) {
        return jdbc.update(
                "DELETE FROM headline_share_records WHERE token_digest = ? AND created_by_user_id = ?",
                tokenDigest,
                userId
        ) == 1;
    }

    @Override
    public int deleteExpiredHeadline(Instant activeAt) {
        return jdbc.update(
                "DELETE FROM headline_share_records WHERE expires_at <= ? OR revoked_at IS NOT NULL",
                Timestamp.from(activeAt)
        );
    }

    private List<ShareService.Claim> claims(long recordId) {
        List<ClaimRow> claims = jdbc.query(
                """
                SELECT id, claim_order, claim_text, status, easy_reason
                FROM health_claims WHERE health_analysis_record_id = ? ORDER BY claim_order
                """,
                (result, rowNumber) -> new ClaimRow(
                        result.getLong("id"), result.getInt("claim_order"),
                        result.getString("claim_text"), result.getString("status"),
                        result.getString("easy_reason")
                ),
                recordId
        );
        Map<Long, List<ShareService.Evidence>> evidences = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT relation.health_claim_id, evidence.source_kind, evidence.title,
                       evidence.provider_name, evidence.publication_date, evidence.source_url,
                       relation.summary
                FROM health_claim_evidences relation
                JOIN health_evidences evidence ON evidence.id = relation.health_evidence_id
                JOIN health_claims claim ON claim.id = relation.health_claim_id
                WHERE claim.health_analysis_record_id = ?
                ORDER BY claim.claim_order, relation.evidence_order
                """,
                result -> {
                    long claimId = result.getLong("health_claim_id");
                    evidences.computeIfAbsent(claimId, ignored -> new ArrayList<>()).add(
                            new ShareService.Evidence(
                                    result.getString("source_kind"), result.getString("title"),
                                    result.getString("provider_name"),
                                    result.getDate("publication_date") == null
                                            ? null : result.getDate("publication_date").toLocalDate(),
                                    result.getString("source_url"), result.getString("summary")
                            )
                    );
                },
                recordId
        );
        return claims.stream().map(claim -> new ShareService.Claim(
                claim.order(), claim.claim(), claim.status(), claim.reason(),
                List.copyOf(evidences.getOrDefault(claim.id(), List.of()))
        )).toList();
    }

    private static HealthRow healthRow(java.sql.ResultSet result) throws java.sql.SQLException {
        return new HealthRow(
                result.getLong("id"),
                new ShareService.Article(
                        result.getString("article_url"), result.getString("article_title"),
                        result.getString("name"), offset(result.getTimestamp("article_published_at")),
                        offset(result.getTimestamp("article_modified_at"))
                ),
                result.getTimestamp("analyzed_at").toInstant(),
                result.getString("overall_status"),
                result.getBigDecimal("verification_rate").toPlainString(),
                result.getInt("supported_claim_count"),
                result.getInt("total_claim_count"),
                result.getString("expert_review_status")
        );
    }

    private static OffsetDateTime offset(Timestamp value) {
        if (value == null) {
            return null;
        }
        LocalDateTime local = value.toLocalDateTime();
        return local.atOffset(ZoneOffset.UTC);
    }

    private static byte[] digest(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record HealthRow(
            long id,
            ShareService.Article article,
            Instant analyzedAt,
            String overallStatus,
            String confirmationRate,
            int confirmedClaimCount,
            int totalClaimCount,
            String expertReviewStatus
    ) {
    }

    private record HealthShareRow(Instant expiresAt, HealthRow health) {
    }

    private record HeadlineShareRow(
            long id,
            Instant expiresAt,
            ShareService.Article article,
            Instant analyzedAt,
            String alternativeHeadline
    ) {
    }

    private record ClaimRow(long id, int order, String claim, String status, String reason) {
    }
}
