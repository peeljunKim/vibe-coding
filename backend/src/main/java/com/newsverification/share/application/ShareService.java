/* 공유 링크 Application 계약 */
package com.newsverification.share.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 건강·제목 결과 공유 생성과 공개 조회 */
public interface ShareService {

    Created createHealth(String userId, long recordId);

    Created createHeadline(String userId, String analysisId);

    HealthResult findHealth(String shareToken);

    HeadlineResult findHeadline(String shareToken);

    void revokeHealth(String userId, String shareToken);

    void revokeHeadline(String userId, String shareToken);

    record Created(String shareType, String shareToken, Instant expiresAt) {
    }

    record Article(
            String url,
            String title,
            String publisher,
            OffsetDateTime publishedAt,
            OffsetDateTime modifiedAt
    ) {
    }

    record Evidence(
            String sourceKind,
            String title,
            String provider,
            LocalDate publishedOrUpdatedDate,
            String sourceUrl,
            String summary
    ) {
    }

    record Claim(
            int order,
            String claim,
            String status,
            String reason,
            List<Evidence> evidences
    ) {
    }

    record HealthResult(
            String shareType,
            Instant expiresAt,
            Article article,
            Instant analyzedAt,
            String overallStatus,
            String confirmationRate,
            int confirmedClaimCount,
            int totalClaimCount,
            List<Claim> claims,
            String expertReviewStatus,
            boolean limitedEvidence
    ) {
    }

    record HeadlineIssue(String type, String explanation) {
    }

    record HeadlineResult(
            String shareType,
            Instant expiresAt,
            Article article,
            Instant analyzedAt,
            List<HeadlineIssue> issues,
            String alternativeHeadline
    ) {
    }
}
