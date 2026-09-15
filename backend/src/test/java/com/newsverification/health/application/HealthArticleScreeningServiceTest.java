/* 건강 기사 분야 판별 경계 검증 */
package com.newsverification.health.application;

import com.newsverification.article.application.ArticleHtmlExtractor;
import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.article.application.HostResolver;
import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.article.application.SafeArticleReader;
import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.publisher.application.PublisherDomainAccessService;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 같은 지원 언론사의 기사별 분야 판별 */
class HealthArticleScreeningServiceTest {

    private static final URI HEALTH_ARTICLE_URL = URI.create("https://news.example/health/1");
    private static final URI GENERAL_ARTICLE_URL = URI.create("https://news.example/general/1");

    /** 건강 기사의 후속 분석 허용 */
    @Test
    void allowsExtractedHealthArticleToContinue() throws Exception {
        var service = service(Map.of(
                HEALTH_ARTICLE_URL, HealthArticleTopicDecision.HEALTH_RELATED
        ));

        var result = service.screen(HEALTH_ARTICLE_URL.toString());

        assertThat(result.article().title()).isEqualTo("독감 예방접종 대상과 시기 안내");
        assertThat(result.decision()).isEqualTo(HealthArticleTopicDecision.HEALTH_RELATED);
        assertThat(result.canContinue()).isTrue();
    }

    /** 일반 기사의 후속 건강 분석 중단 */
    @Test
    void stopsExtractedGeneralArticleFromHealthAnalysis() throws Exception {
        var service = service(Map.of(
                GENERAL_ARTICLE_URL, HealthArticleTopicDecision.NOT_HEALTH_RELATED
        ));

        var result = service.screen(GENERAL_ARTICLE_URL.toString());

        assertThat(result.article().title()).isEqualTo("지역 축제 개막과 교통 통제 안내");
        assertThat(result.decision()).isEqualTo(HealthArticleTopicDecision.NOT_HEALTH_RELATED);
        assertThat(result.canContinue()).isFalse();
    }

    /** 실제 기사 수집 경계와 테스트 판별 Port 구성 */
    private HealthArticleScreeningService service(
            Map<URI, HealthArticleTopicDecision> decisions
    ) throws Exception {
        PublisherDomainAccessService accessService = mock(PublisherDomainAccessService.class);
        when(accessService.requireActivePublisherHosts("news.example"))
                .thenReturn(Set.of("news.example"));

        HostResolver resolver = hostname -> List.of(
                InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleUrlValidator validator = new ArticleUrlValidator(resolver);
        ArticleHttpClient httpClient = (target, timeout, maxResponseBytes) ->
                htmlResponse(target.uri());
        SafeArticleReader safeReader = new SafeArticleReader(
                validator,
                new ArticleHtmlExtractor(),
                httpClient,
                Duration.ofSeconds(10),
                2 * 1024 * 1024,
                3
        );
        PublisherArticleReader articleReader = new PublisherArticleReader(
                validator,
                accessService,
                safeReader
        );

        return new HealthArticleScreeningService(
                articleReader,
                new MockHealthArticleTopicClassifier(decisions)
        );
    }

    /** 기사별 고정 HTML 응답 */
    private ArticleHttpResponse htmlResponse(URI articleUrl) {
        String html = articleUrl.equals(HEALTH_ARTICLE_URL)
                ? articleHtml(
                        "독감 예방접종 대상과 시기 안내",
                        "질병관리청은 고위험군의 독감 예방접종 대상과 권장 시기를 안내했습니다.")
                : articleHtml(
                        "지역 축제 개막과 교통 통제 안내",
                        "지역 축제 개막식과 행사장 주변 교통 통제 시간이 발표됐습니다.");
        return new ArticleHttpResponse(
                200,
                "text/html; charset=UTF-8",
                html,
                html.getBytes(StandardCharsets.UTF_8).length,
                null
        );
    }

    /** 정제 대상 기사 HTML */
    private String articleHtml(String title, String body) {
        return """
                <html lang="ko"><head>
                <meta property="og:title" content="%s">
                <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
                </head><body><article><p>%s</p></article></body></html>
                """.formatted(title, body);
    }

    /** Gemini 대체용 고정 판별 구현 */
    private static final class MockHealthArticleTopicClassifier
            implements HealthArticleTopicClassifier {

        private final Map<URI, HealthArticleTopicDecision> decisions;

        /** 기사 URL별 고정 판별값 구성 */
        private MockHealthArticleTopicClassifier(
                Map<URI, HealthArticleTopicDecision> decisions
        ) {
            this.decisions = decisions;
        }

        @Override
        public HealthArticleTopicDecision classify(ExtractedArticle article) {
            HealthArticleTopicDecision decision = decisions.get(article.sourceUrl());
            if (decision == null) {
                throw new AssertionError("Unexpected extracted article: " + article.sourceUrl());
            }
            return decision;
        }
    }
}
