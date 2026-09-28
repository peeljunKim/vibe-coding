/* 공유 링크 Application 서비스 */
package com.newsverification.share.application;

import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 소유권·ACTIVE 상태·7일 만료 적용 */
@Service
public class DefaultShareService implements ShareService {

    private static final Duration SHARE_LIFETIME = Duration.ofDays(7);
    private final ShareStore store;
    private final HeadlineAnalysisJobService headlineJobs;
    private final ShareTokenGenerator tokenGenerator;
    private final Clock clock;

    public DefaultShareService(
            ShareStore store,
            HeadlineAnalysisJobService headlineJobs,
            ShareTokenGenerator tokenGenerator,
            Clock clock
    ) {
        this.store = store;
        this.headlineJobs = headlineJobs;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Override
    public Created createHealth(String userId, long recordId) {
        long ownerId = activeUserId(userId);
        Instant now = clock.instant();
        ShareStore.HealthSource source = store.findHealthSource(recordId, ownerId, now)
                .orElseThrow(() -> new ShareException("SHARE_SOURCE_UNAVAILABLE"));
        String token = tokenGenerator.generate();
        Instant expiresAt = now.plus(SHARE_LIFETIME);
        store.saveHealth(recordId, digest(token), summary(source.overallStatus()), now, expiresAt);
        return new Created("HEALTH", token, expiresAt);
    }

    @Override
    public Created createHeadline(String userId, String analysisId) {
        long ownerId = activeUserId(userId);
        if (analysisId == null || analysisId.isBlank()) {
            throw new ShareException("SHARE_SOURCE_UNAVAILABLE");
        }
        HeadlineAnalysisJobService.Requester requester =
                new HeadlineAnalysisJobService.Requester(Long.toString(ownerId), null, null, null);
        HeadlineAnalysisJobService.Progress progress = headlineJobs.find(analysisId, requester)
                .filter(value -> value.status() == AnalysisJobStatus.COMPLETED && value.result() != null)
                .orElseThrow(() -> new ShareException("SHARE_SOURCE_UNAVAILABLE"));
        HeadlineAnalysisResult result = progress.result();
        if (!hasText(result.aiModelVersion()) || !hasText(result.policyVersion())) {
            throw new ShareException("SHARE_SOURCE_UNAVAILABLE");
        }
        String token = tokenGenerator.generate();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(SHARE_LIFETIME);
        List<HeadlineIssue> issues = result.issues().stream()
                .map(issue -> new HeadlineIssue(issue.type().name(), issue.explanation()))
                .toList();
        store.saveHeadline(
                ownerId,
                new Article(
                        result.article().url().toASCIIString(), result.article().title(),
                        result.article().publisher(), result.article().publishedAt(), result.article().modifiedAt()
                ),
                digest(token), result.alternativeHeadline(), summary(issues.get(0).type()),
                result.analyzedAt(), now, expiresAt, result.aiModelVersion(), result.policyVersion(), issues
        );
        return new Created("HEADLINE", token, expiresAt);
    }

    @Override
    public HealthResult findHealth(String shareToken) {
        return store.findHealth(digest(validToken(shareToken)), clock.instant())
                .orElseThrow(() -> new ShareException("SHARE_NOT_FOUND"));
    }

    @Override
    public HeadlineResult findHeadline(String shareToken) {
        return store.findHeadline(digest(validToken(shareToken)), clock.instant())
                .orElseThrow(() -> new ShareException("SHARE_NOT_FOUND"));
    }

    @Override
    public void revokeHealth(String userId, String shareToken) {
        if (!store.revokeHealth(digest(validToken(shareToken)), userId(userId), clock.instant())) {
            throw new ShareException("SHARE_NOT_FOUND");
        }
    }

    @Override
    public void revokeHeadline(String userId, String shareToken) {
        if (!store.deleteHeadline(digest(validToken(shareToken)), userId(userId))) {
            throw new ShareException("SHARE_NOT_FOUND");
        }
    }

    private long activeUserId(String value) {
        long userId = userId(value);
        if (!store.isActiveUser(userId)) {
            throw new ShareException("SHARE_ACCESS_DENIED");
        }
        return userId;
    }

    private static long userId(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ShareException("SHARE_ACCESS_DENIED");
        }
    }

    private static String validToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,128}")) {
            throw new ShareException("SHARE_NOT_FOUND");
        }
        return value;
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String summary(String value) {
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
