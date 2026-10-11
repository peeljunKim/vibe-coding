/* 기사 HTML 정제와 필수 정보 추출 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

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
    void extractsRequiredArticleContentFromMockHtml() {
        String html = """
                <meta property="og:title" content="비타민 D 연구 결과를 확인했습니다">
                <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
                <meta property="article:modified_time" content="2026-08-14T10:00:00+09:00">
                <nav>메뉴 영역</nav>
                <article itemprop="articleBody">
                  <p>연구진은 비타민 D와 건강 지표의 연관성을 분석했습니다.</p>
                  <aside class="advertisement">광고 영역</aside>
                  <p>연구 결과만으로 예방 효과를 단정할 수는 없습니다.</p>
                  <div class="comments">댓글 영역</div>
                </article>
                """;

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

    /** JSON-LD 게시일과 KBS 본문 Container 추출 */
    @Test
    void extractsKbsArticleFromStructuredDateAndViewContainer() {
        String html = """
                <meta property="og:title" content="지역 의료 지원 정책을 확대합니다">
                <script type="application/ld+json">
                {"@type":"NewsArticle","datePublished":"2026-08-14 09:30:00",}
                </script>
                <div class="view-article">
                  <p>지역 의료기관을 지원하는 정책이 발표됐습니다.</p>
                  <p>지원 대상과 적용 시기는 추가 안내될 예정입니다.</p>
                  <p>■ 제보하기 ▷ 전화 : 02-000-0000</p>
                </div>
                <div class="related">관련 기사 목록</div>
                """;

        var article = extractor.extract(
                URI.create("https://news.kbs.co.kr/news/pc/view/view.do?ncd=1"),
                html
        );

        assertThat(article.title()).isEqualTo("지역 의료 지원 정책을 확대합니다");
        assertThat(article.publishedAt()).hasToString("2026-08-14T09:30+09:00");
        assertThat(article.body()).isEqualTo(
                "지역 의료기관을 지원하는 정책이 발표됐습니다.\n"
                        + "지원 대상과 적용 시기는 추가 안내될 예정입니다."
        );
        assertThat(article.body()).doesNotContain("관련 기사 목록");
    }

    /** 한국일보 본문 Container 추출 */
    @Test
    void extractsHankookilboArticleFromArticleViewContent() {
        String html = """
                <meta property="og:title" content="지역 공공의료 서비스를 강화합니다">
                <meta property="article:published_time" content="2026-08-15T11:20:00+09:00">
                <div id="article-view-content">
                  <p>지역 공공병원의 의료 인력을 확충합니다.</p>
                  <p>세부 지원 계획은 단계적으로 시행됩니다.</p>
                </div>
                <div class="related">함께 읽는 기사입니다.</div>
                """;

        var article = extractor.extract(
                URI.create("https://www.hankookilbo.com/news/article/A1"),
                html
        );

        assertThat(article.title()).isEqualTo("지역 공공의료 서비스를 강화합니다");
        assertThat(article.publishedAt()).hasToString("2026-08-15T11:20+09:00");
        assertThat(article.body()).isEqualTo(
                "지역 공공병원의 의료 인력을 확충합니다.\n"
                        + "세부 지원 계획은 단계적으로 시행됩니다."
        );
        assertThat(article.body()).doesNotContain("함께 읽는 기사");
    }

    /** 기사 문맥의 닫기 표현 보존 */
    @Test
    void preservesStandaloneCloseWordInArticleBody() {
        String html = """
                <meta property="og:title" content="지역 상점 운영 기사">
                <meta property="article:published_time" content="2026-10-11T09:00:00+09:00">
                <article><p>지역 상점의 문 닫기 결정이 주민에게 알려졌습니다.</p></article>
                """;

        var article = extractor.extract(URI.create("https://news.example/article/close"), html);

        assertThat(article.body()).contains("문 닫기 결정");
    }

    /** 지원 언론사 본문 구조 회귀 검증 */
    @Test
    void extractsSupportedPublisherArticleContainers() {
        List<PublisherStructureCase> cases = List.of(
                new PublisherStructureCase("뉴시스", """
                        <div class="viewer">
                          구글에서 선호하는 매체로 추가
                          <p>본문 첫 문단입니다.</p><p>본문 둘째 문단입니다.</p>
                          <p>◎공감언론 뉴시스 reporter@example.com</p>
                        </div>
                        """),
                new PublisherStructureCase("YTN", """
                        <div id="CmAdContent">
                          본문 첫 문단입니다.<br>본문 둘째 문단입니다.
                          ※ '당신의 제보가 뉴스가 됩니다' 이후 안내 문구
                        </div>
                        """),
                new PublisherStructureCase("동아일보", """
                        <section class="news_view">
                          본문 첫 문단입니다.<br>본문 둘째 문단입니다.
                        </section>
                        """),
                new PublisherStructureCase("서울신문", """
                        <div class="viewContent">
                          구글에서 서울신문 먼저 보기 이미지 확대 닫기
                          <p>본문 첫 문단입니다.</p><div>본문 둘째 문단입니다.</div>
                          <div>Copyright ⓒ 언론사 안내 문구</div>
                        </div>
                        """),
                new PublisherStructureCase("헬스조선", """
                        <div class="news_body">
                          본문 첫 문단입니다.<br>본문 둘째 문단입니다.
                          <div class="news_relArt">관련 기사</div>
                          <div class="news_copyright">무단 전재 안내</div>
                        </div>
                        """),
                new PublisherStructureCase("코메디닷컴", """
                        <div class="entry-content">
                          <p>본문 첫 문단입니다.</p><p>본문 둘째 문단입니다.</p>
                        </div>
                        <article><p>추천 기사</p></article>
                        """),
                new PublisherStructureCase("국민일보", """
                        <div class="view_cont">
                          <p>본문 첫 문단입니다.</p><p>본문 둘째 문단입니다.</p>
                          <p>기자 이름 GoodNews paper ⓒ 국민일보, 무단전재 금지</p>
                        </div>
                        """)
        );

        for (PublisherStructureCase structureCase : cases) {
            String html = """
                    <meta property="og:title" content="구조 검증 기사">
                    <meta property="article:published_time" content="2026-10-01T09:00:00+09:00">
                    %s
                    """.formatted(structureCase.bodyHtml());
            var article = extractor.extract(
                    URI.create("https://news.example/structure"),
                    html
            );

            assertThat(article.body())
                    .as(structureCase.publisher())
                    .contains("본문 첫 문단입니다.")
                    .contains("본문 둘째 문단입니다.")
                    .doesNotContain(
                            "관련 기사",
                            "추천 기사",
                            "무단 전재",
                            "공유하기",
                            "구글에서",
                            "이미지 확대",
                            "닫기",
                            "당신의 제보가 뉴스가 됩니다",
                            "공감언론",
                            "GoodNews paper",
                            "ⓒ"
                    );
        }
    }

    /** 배열형 NewsArticle 구조화 게시일 추출 */
    @Test
    void extractsPublishedDateWhenNewsArticleTypeIsArray() {
        String html = """
                <meta property="og:title" content="지역 건강 정책 기사">
                <script type="application/ld+json">
                {"@type":["Thing","NewsArticle"],"datePublished":"2026-08-16T13:40:00+09:00"}
                </script>
                <article><p>지역 건강 정책을 안내하는 기사 본문입니다.</p></article>
                """;

        var article = extractor.extract(URI.create("https://news.example/article/array"), html);

        assertThat(article.publishedAt()).hasToString("2026-08-16T13:40+09:00");
    }

    /** 비표준 배열형 NewsArticle 구조화 게시일 추출 */
    @Test
    void extractsPublishedDateFromMalformedNewsArticleTypeArray() {
        String html = """
                <meta property="og:title" content="지역 의료 지원 기사">
                <script type="application/ld+json">
                {"@type":["Thing","NewsArticle"],"datePublished":"2026-08-17 08:10:00",}
                </script>
                <article><p>지역 의료 지원 내용을 안내하는 기사 본문입니다.</p></article>
                """;

        var article = extractor.extract(URI.create("https://news.example/article/malformed-array"), html);

        assertThat(article.publishedAt()).hasToString("2026-08-17T08:10+09:00");
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

    /** 언론사별 최소 본문 구조 */
    private record PublisherStructureCase(String publisher, String bodyHtml) {
    }
}
