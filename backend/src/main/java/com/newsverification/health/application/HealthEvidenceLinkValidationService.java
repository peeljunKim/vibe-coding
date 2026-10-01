/* 건강 근거 링크 재확인과 제한 결과 생성 */
package com.newsverification.health.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 일시 오류 1회 재확인과 깨진 근거 의존 주장 제거 */
public class HealthEvidenceLinkValidationService {

    private final HealthEvidenceLinkChecker checker;

    /** 근거 링크 상태 확인 Port 구성 */
    public HealthEvidenceLinkValidationService(HealthEvidenceLinkChecker checker) {
        this.checker = Objects.requireNonNull(checker);
    }

    /** Cache 결과의 근거 링크 상태 집계 */
    public HealthEvidenceLinkValidation validate(HealthAnalysisResult result) {
        Objects.requireNonNull(result);
        Set<URI> missingUrls = new LinkedHashSet<>();
        boolean temporaryFailure = false;

        for (URI sourceUrl : sourceUrls(result)) {
            HealthEvidenceLinkChecker.Status status = checker.check(sourceUrl);
            if (status == HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE) {
                status = checker.check(sourceUrl);
            }
            if (status == HealthEvidenceLinkChecker.Status.MISSING) {
                missingUrls.add(sourceUrl.normalize());
            } else if (status == HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE) {
                temporaryFailure = true;
            }
        }

        if (!missingUrls.isEmpty()) {
            return new HealthEvidenceLinkValidation(
                    HealthEvidenceLinkValidation.Status.MISSING,
                    Optional.of(limitedResult(result, missingUrls))
            );
        }
        return new HealthEvidenceLinkValidation(
                temporaryFailure
                        ? HealthEvidenceLinkValidation.Status.TEMPORARY_FAILURE
                        : HealthEvidenceLinkValidation.Status.AVAILABLE,
                Optional.empty()
        );
    }

    /** 중복을 제거한 근거 주소 순서 보존 */
    private Set<URI> sourceUrls(HealthAnalysisResult result) {
        Set<URI> sourceUrls = new LinkedHashSet<>();
        result.claims().stream()
                .flatMap(claim -> claim.evidences().stream())
                .map(HealthAnalysisResult.Evidence::sourceUrl)
                .filter(Objects::nonNull)
                .map(URI::normalize)
                .forEach(sourceUrls::add);
        return sourceUrls;
    }

    /** 깨진 근거에 의존하지 않는 주장만 남긴 결과 */
    private HealthAnalysisResult limitedResult(
            HealthAnalysisResult result,
            Set<URI> missingUrls
    ) {
        List<HealthAnalysisResult.Claim> remainingClaims = result.claims().stream()
                .filter(claim -> !claim.evidences().isEmpty())
                .filter(claim -> claim.evidences().stream()
                        .map(HealthAnalysisResult.Evidence::sourceUrl)
                        .filter(Objects::nonNull)
                        .map(URI::normalize)
                        .noneMatch(missingUrls::contains))
                .toList();
        int confirmedCount = (int) remainingClaims.stream()
                .filter(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.SUPPORTED)
                .count();
        int totalCount = remainingClaims.size();
        BigDecimal confirmationRate = totalCount == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(confirmedCount * 100L)
                        .divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);

        return new HealthAnalysisResult(
                result.article(),
                result.analyzedAt(),
                overallStatus(remainingClaims, confirmedCount),
                confirmationRate,
                confirmedCount,
                totalCount,
                remainingClaims,
                result.expertReviewStatus(),
                result.aiModelVersion(),
                result.policyVersion(),
                result.evidenceAllowlistVersion(),
                true
        );
    }

    /** 고정 판정 규칙 기반 종합 상태 재계산 */
    private HealthAnalysisResult.OverallStatus overallStatus(
            List<HealthAnalysisResult.Claim> claims,
            int confirmedCount
    ) {
        if (claims.isEmpty()) {
            return HealthAnalysisResult.OverallStatus.CAUTION;
        }
        long contradictedCount = claims.stream()
                .filter(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.CONTRADICTED)
                .count();
        boolean firstClaimContradicted = claims.stream()
                .min(java.util.Comparator.comparingInt(HealthAnalysisResult.Claim::order))
                .map(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.CONTRADICTED)
                .orElse(false);
        if (firstClaimContradicted || contradictedCount * 2 >= claims.size()) {
            return HealthAnalysisResult.OverallStatus.DOUBTFUL;
        }
        if (contradictedCount == 0 && confirmedCount * 3 >= claims.size() * 2) {
            return HealthAnalysisResult.OverallStatus.RELIABLE;
        }
        return HealthAnalysisResult.OverallStatus.CAUTION;
    }

    /** 외부 확인 없는 기존 단위 테스트 기본값 */
    static HealthEvidenceLinkValidationService trustAll() {
        return new HealthEvidenceLinkValidationService(
                sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
        );
    }
}
