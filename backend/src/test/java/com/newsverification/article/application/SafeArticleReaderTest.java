/* Redirect 재검증과 기사 수집 흐름 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mock HTTP 경계 기반 안전한 기사 수집 */
class SafeArticleReaderTest {

    private static final String ARTICLE_HTML = """
            <html lang="ko"><head>
            <meta property="og:title" content="건강 기사 제목">
            <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
            </head><body><article><p>검증할 건강 기사 본문입니다.</p></article></body></html>
            """;

    /** Redirect 목적지 재검증 후 기사 추출 */
    @Test
    void validatesRedirectDestinationBeforeExtractingArticle() {
        HostResolver resolver = hostname ->
                List.of(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleHttpClient httpClient = fakeClient(Map.of(
                URI.create("https://news.example/start"),
                redirect("/articles/123"),
                URI.create("https://news.example/articles/123"),
                html(ARTICLE_HTML)
        ));
        var reader = reader(resolver, httpClient);

        var article = reader.read("https://news.example/start", Set.of("news.example"));

        assertThat(article.sourceUrl()).isEqualTo(URI.create("https://news.example/articles/123"));
        assertThat(article.title()).isEqualTo("건강 기사 제목");
    }

    /** 내부 IP Redirect 요청 전 차단 */
    @Test
    void rejectsRedirectResolvedToPrivateAddressBeforeSecondRequest() {
        HostResolver resolver = hostname -> hostname.equals("internal.example")
                ? List.of(InetAddress.getByAddress(new byte[]{10, 0, 0, 1}))
                : List.of(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleHttpClient httpClient = fakeClient(Map.of(
                URI.create("https://news.example/start"),
                redirect("https://internal.example/private")
        ));
        var reader = reader(resolver, httpClient);

        assertThatThrownBy(() -> reader.read(
                "https://news.example/start",
                Set.of("news.example", "internal.example")
        ))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.UNSAFE_ADDRESS);
    }

    /** 원본 HTML 응답 크기 제한 */
    @Test
    void rejectsResponseLargerThanConfiguredLimit() {
        HostResolver resolver = hostname ->
                List.of(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleHttpClient httpClient = fakeClient(Map.of(
                URI.create("https://news.example/large"),
                new ArticleHttpResponse(200, "text/html", ARTICLE_HTML, 101, null)
        ));
        var reader = new SafeArticleReader(
                new ArticleUrlValidator(resolver),
                new ArticleHtmlExtractor(),
                httpClient,
                Duration.ofSeconds(5),
                100,
                5
        );

        assertThatThrownBy(() -> reader.read(
                "https://news.example/large",
                Set.of("news.example")
        ))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.RESPONSE_TOO_LARGE);
    }

    /** 테스트용 기사 Reader 구성 */
    private SafeArticleReader reader(HostResolver resolver, ArticleHttpClient httpClient) {
        return new SafeArticleReader(
                new ArticleUrlValidator(resolver),
                new ArticleHtmlExtractor(),
                httpClient,
                Duration.ofSeconds(5),
                2 * 1024 * 1024,
                5
        );
    }

    /** 고정 응답 HTTP 경계 */
    private ArticleHttpClient fakeClient(Map<URI, ArticleHttpResponse> responses) {
        return (uri, timeout, maxResponseBytes) -> {
            ArticleHttpResponse response = responses.get(uri);
            if (response == null) {
                throw new AssertionError("Unexpected HTTP request: " + uri);
            }
            return response;
        };
    }

    /** 테스트용 HTML 응답 */
    private ArticleHttpResponse html(String body) {
        return new ArticleHttpResponse(
                200,
                "text/html; charset=UTF-8",
                body,
                body.getBytes(StandardCharsets.UTF_8).length,
                null
        );
    }

    /** 테스트용 Redirect 응답 */
    private ArticleHttpResponse redirect(String location) {
        return new ArticleHttpResponse(302, null, "", 0, location);
    }
}
