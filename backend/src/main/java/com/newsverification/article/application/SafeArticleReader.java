/* URL 재검증 기반 기사 수집 조정 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** Redirect마다 보안 정책을 적용하는 기사 Reader */
public final class SafeArticleReader {

    private final ArticleUrlValidator urlValidator;
    private final ArticleHtmlExtractor htmlExtractor;
    private final ArticleHttpClient httpClient;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final int maxRedirects;

    public SafeArticleReader(
            ArticleUrlValidator urlValidator,
            ArticleHtmlExtractor htmlExtractor,
            ArticleHttpClient httpClient,
            Duration requestTimeout,
            int maxResponseBytes,
            int maxRedirects
    ) {
        this.urlValidator = urlValidator;
        this.htmlExtractor = htmlExtractor;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
    }

    /** 검증된 최종 URL의 기사 추출 */
    public ExtractedArticle read(String rawUrl, Set<String> allowedHosts) {
        String currentUrl = rawUrl;
        for (int redirectCount = 0; redirectCount <= maxRedirects; redirectCount++) {
            URI currentUri = urlValidator.validate(currentUrl, allowedHosts);
            ArticleHttpResponse response = request(currentUri);

            if (isRedirect(response.statusCode())) {
                if (redirectCount == maxRedirects) {
                    throw new ArticleProcessingException(ArticleProcessingError.TOO_MANY_REDIRECTS);
                }
                currentUrl = resolveRedirect(currentUri, response.location()).toString();
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ArticleProcessingException(ArticleProcessingError.HTTP_ERROR);
            }
            if (response.contentType() == null
                    || !response.contentType().toLowerCase().startsWith("text/html")) {
                throw new ArticleProcessingException(ArticleProcessingError.UNSUPPORTED_CONTENT_TYPE);
            }
            if (response.bodyBytes() > maxResponseBytes) {
                throw new ArticleProcessingException(ArticleProcessingError.RESPONSE_TOO_LARGE);
            }
            return htmlExtractor.extract(currentUri, response.body());
        }
        throw new ArticleProcessingException(ArticleProcessingError.TOO_MANY_REDIRECTS);
    }

    /** 외부 HTTP 실패 변환 */
    private ArticleHttpResponse request(URI uri) {
        try {
            return httpClient.get(uri, requestTimeout, maxResponseBytes);
        }
        catch (IOException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.DOWNLOAD_FAILED, exception);
        }
    }

    /** Redirect 목적지 해석 */
    private URI resolveRedirect(URI currentUri, String location) {
        if (location == null || location.isBlank()) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_REDIRECT);
        }
        try {
            return currentUri.resolve(location);
        }
        catch (IllegalArgumentException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_REDIRECT, exception);
        }
    }

    /** 지원 Redirect 상태 확인 */
    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }
}
