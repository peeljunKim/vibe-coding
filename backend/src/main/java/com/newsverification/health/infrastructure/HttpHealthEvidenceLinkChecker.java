/* 건강 근거 링크 HTTP 상태 확인 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.health.application.HealthEvidenceLinkChecker;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** 허용 출처와 공개 IP를 확인하는 운영 근거 링크 Adapter */
public final class HttpHealthEvidenceLinkChecker implements HealthEvidenceLinkChecker {

    private final ArticleUrlValidator urlValidator;
    private final ArticleHttpClient httpClient;
    private final Set<String> allowedHosts;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final int maxRedirects;

    /** 근거 링크 요청 제한과 허용 Host 구성 */
    public HttpHealthEvidenceLinkChecker(
            ArticleUrlValidator urlValidator,
            ArticleHttpClient httpClient,
            Set<String> allowedHosts,
            Duration requestTimeout,
            int maxResponseBytes,
            int maxRedirects
    ) {
        this.urlValidator = Objects.requireNonNull(urlValidator);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
        if (this.allowedHosts.isEmpty() || requestTimeout.isZero() || requestTimeout.isNegative()
                || maxResponseBytes < 1 || maxRedirects < 0) {
            throw new IllegalArgumentException("Invalid evidence link policy");
        }
    }

    /** 근거 링크 단일 상태 확인 */
    @Override
    public Status check(URI sourceUrl) {
        if (sourceUrl == null) {
            return Status.MISSING;
        }
        try {
            URI currentUri = sourceUrl;
            int followedRedirects = 0;
            while (true) {
                var target = urlValidator.validate(currentUri.toString(), allowedHosts);
                var response = httpClient.get(target, requestTimeout, maxResponseBytes);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return Status.AVAILABLE;
                }
                if (response.statusCode() == 404 || response.statusCode() == 410) {
                    return Status.MISSING;
                }
                if (!isRedirect(response.statusCode())) {
                    return Status.TEMPORARY_FAILURE;
                }
                if (followedRedirects >= maxRedirects
                        || response.location() == null
                        || response.location().isBlank()) {
                    return Status.MISSING;
                }
                currentUri = currentUri.resolve(response.location());
                followedRedirects++;
            }
        }
        catch (IOException exception) {
            return Status.TEMPORARY_FAILURE;
        }
        catch (ArticleProcessingException exception) {
            return exception.error() == ArticleProcessingError.DNS_LOOKUP_FAILED
                    ? Status.TEMPORARY_FAILURE
                    : Status.MISSING;
        }
        catch (IllegalArgumentException exception) {
            return Status.MISSING;
        }
    }

    /** 수동 추적 대상 Redirect 응답 판별 */
    private static boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }
}
