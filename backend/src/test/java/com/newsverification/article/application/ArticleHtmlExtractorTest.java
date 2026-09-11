/* 기사 HTML 정제와 필수 정보 추출 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mock HTML 기반 기사 추출 규칙 */
class ArticleHtmlExtractorTest {

    private final ArticleHtmlExtractor extractor = new ArticleHtmlExtractor();

    /** 수정일 오류와 게시일 오류 구분 */
    @Test
    void reportsMalformedModifiedDateSeparately() {
        String html = """
                <meta property="og:title" content="건강 기사">
                <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
                <meta property="article:modified_time" content="invalid-date">
                <article><p>건강 기사 본문입니다.</p></article>
                """;
        assertThatThrownBy(() -> extractor.extract(URI.create("https://news.example/article"), html))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error().name())
                .isEqualTo("INVALID_MODIFIED_AT");
    }

    /** 제목·날짜·정제 본문 추출 */
    @Test
    void extractsRequiredArticleContentFromMockHtml() throws IOException {
        String html;
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/articles/generic-news.html")
        )) {
            html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var article = extractor.extract(URI.create("https://news.example/article/123"), html);

        assertThat(article.title()).isEqualTo("비타민 D 연구 결과를 확인했습니다");
        assertThat(article.publishedAt()).hasToString("2026-08-14T09:30+09:00");
        assertThat(article.modifiedAt()).get().hasToString("2026-08-14T10:00+09:00");
        assertThat(article.body()).isEqualTo(
                "연구진은 비타민 D와 건강 지표의 연관성을 분석했습니다.\n"
                        + "연구 결과만으로 예방 효과를 단정할 수는 없습니다."
        );
        assertThat(article.body()).doesNotContain("광고", "댓글", "메뉴");
    }

    /** 본문 최대 글자 수 초과 차단 */
    @Test
    void rejectsArticleBodyLongerThanTwentyThousandCharacters() {
        String body = "가".repeat(20_001);
        String html = """
                <html lang="ko"><head>
                <meta property="og:title" content="긴 기사">
                <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
                </head><body><article><p>%s</p></article></body></html>
                """.formatted(body);

        assertThatThrownBy(() -> extractor.extract(
                URI.create("https://news.example/article/long"),
                html
        ))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.ARTICLE_TOO_LONG);
    }
}
