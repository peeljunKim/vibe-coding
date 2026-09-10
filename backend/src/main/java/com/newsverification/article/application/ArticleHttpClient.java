/* 기사 HTTP 요청 경계 */
package com.newsverification.article.application;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** 외부 기사 응답 수신 경계 */
@FunctionalInterface
public interface ArticleHttpClient {

    /** Redirect 미추적 단일 요청 */
    ArticleHttpResponse get(URI uri, Duration timeout, int maxResponseBytes) throws IOException;
}
