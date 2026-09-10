/* Jsoup 기반 기사 HTTP 요청 */
package com.newsverification.article.infrastructure;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** Redirect를 자동 추적하지 않는 기사 Client */
public final class JsoupArticleHttpClient implements ArticleHttpClient {

    private static final String USER_AGENT = "NewsVerificationBot/0.1";

    /** 크기와 시간 제한이 적용된 단일 GET 요청 */
    @Override
    public ArticleHttpResponse get(URI uri, Duration timeout, int maxResponseBytes) throws IOException {
        Connection.Response response = Jsoup.connect(uri.toString())
                .userAgent(USER_AGENT)
                .timeout(Math.toIntExact(timeout.toMillis()))
                .maxBodySize(Math.addExact(maxResponseBytes, 1))
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .execute();
        byte[] bodyBytes = response.bodyAsBytes();
        return new ArticleHttpResponse(
                response.statusCode(),
                response.contentType(),
                response.body(),
                bodyBytes.length,
                response.header("Location")
        );
    }
}
