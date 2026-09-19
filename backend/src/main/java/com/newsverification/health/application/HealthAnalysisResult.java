/* 건강 기사 분석 결과 모델 */
package com.newsverification.health.application;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Mock과 실제 AI Adapter가 공유하는 구조화 결과 */
public record HealthAnalysisResult(
        ArticleSummary article,
        Instant analyzedAt,
        OverallStatus overallStatus,
        BigDecimal confirmationRate,
        int confirmedClaimCount,
        int totalClaimCount,
        List<Claim> claims,
        ExpertReviewStatus expertReviewStatus,
        boolean limitedEvidence
) {

    /** 기사 요약 */
    public record ArticleSummary(
            URI url,
            String title,
            String publisher,
            OffsetDateTime publishedAt,
            OffsetDateTime modifiedAt
    ) {
    }

    /** 핵심 주장 판정 */
    public record Claim(
            int order,
            String claim,
            ClaimStatus status,
            String reason,
            List<Evidence> evidences
    ) {
    }

    /** 근거 출처 요약 */
    public record Evidence(
            String title,
            String provider,
            LocalDate publishedOrUpdatedDate,
            URI sourceUrl,
            String summary,
            String conflictDescription
    ) {
    }

    /** 건강 분석 종합 상태 */
    public enum OverallStatus {
        RELIABLE,
        CAUTION,
        DOUBTFUL
    }

    /** 주장별 판정 상태 */
    public enum ClaimStatus {
        SUPPORTED,
        NEEDS_REVIEW,
        CONTRADICTED,
        INSUFFICIENT
    }

    /** 전문가 검토 상태 */
    public enum ExpertReviewStatus {
        NOT_REVIEWED,
        REVIEWED
    }
}
