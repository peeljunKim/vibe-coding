/* 기사 HTTP 단일 응답 */
package com.newsverification.article.application;

/** Redirect와 HTML 응답 정보 */
public record ArticleHttpResponse(
        int statusCode,
        String contentType,
        String body,
        int bodyBytes,
        String location
) {
}
