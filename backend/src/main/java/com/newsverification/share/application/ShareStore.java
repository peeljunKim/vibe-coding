/* 공유 결과 영속 경계 */
package com.newsverification.share.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Token Digest와 공유 Snapshot 저장 경계 */
public interface ShareStore {

    boolean isActiveUser(long userId);

    Optional<HealthSource> findHealthSource(long recordId, long userId, Instant activeAt);

    void saveHealth(long recordId, byte[] tokenDigest, String shortSummary, Instant createdAt, Instant expiresAt);

    void saveHeadline(
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
    );

    Optional<ShareService.HealthResult> findHealth(byte[] tokenDigest, Instant activeAt);

    Optional<ShareService.HeadlineResult> findHeadline(byte[] tokenDigest, Instant activeAt);

    boolean revokeHealth(byte[] tokenDigest, long userId, Instant revokedAt);

    boolean deleteHeadline(byte[] tokenDigest, long userId);

    int deleteExpiredHeadline(Instant activeAt);

    record HealthSource(
            long recordId,
            ShareService.Article article,
            Instant analyzedAt,
            String overallStatus,
            String confirmationRate,
            int confirmedClaimCount,
            int totalClaimCount,
            List<ShareService.Claim> claims,
            String expertReviewStatus,
            boolean limitedEvidence
    ) {
    }
}
