/* 건강 분석 분야 판별 분기 검증 */
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 건강 기사만 후속 분석으로 전달하는 Use Case */
class HealthAnalysisUseCaseTest {

    private static final URI ARTICLE_URL = URI.create("https://news.example/article/1");
    private static final HealthAnalysisUsageSubject MEMBER = new HealthAnalysisUsageSubject(
            HealthAnalysisUserType.MEMBER,
            "member-key"
    );

    /** 관련 기사의 후속 분석 전달 */
    @Test
    void startsAnalysisForHealthArticle() throws Exception {
        var analysisPort = new MockHealthAnalysisPort();
        var usagePolicy = new MockHealthTopicFailureUsagePolicy();
        var useCase = useCase(
                HealthArticleTopicDecision.HEALTH_RELATED,
                "독감 예방접종 대상과 시기 안내",
                "질병관리청은 고위험군의 독감 예방접종 대상과 권장 시기를 안내했습니다.",
                analysisPort,
                usagePolicy
        );

        var result = useCase.start(ARTICLE_URL.toString(), MEMBER);

        assertThat(result.status()).isEqualTo(HealthAnalysisRoutingStatus.ANALYSIS_STARTED);
        assertThat(result.userMessage()).isEmpty();
        assertThat(result.result()).isPresent();
        assertThat(analysisPort.receivedArticles())
                .extracting(ExtractedArticle::title)
                .containsExactly("독감 예방접종 대상과 시기 안내");
        assertThat(usagePolicy.verificationCount()).isEqualTo(1);
        assertThat(usagePolicy.analysisStartCount()).isEqualTo(1);
        assertThat(usagePolicy.failureCount()).isZero();
    }

    /** 일반 기사의 사용자 안내 후 분석 중단 */
    @Test
    void stopsAnalysisForGeneralArticle() throws Exception {
        var analysisPort = new MockHealthAnalysisPort();
        var usagePolicy = new MockHealthTopicFailureUsagePolicy();
        var useCase = useCase(
                HealthArticleTopicDecision.NOT_HEALTH_RELATED,
                "지역 축제 개막과 교통 통제 안내",
                "지역 축제 개막식과 행사장 주변 교통 통제 시간이 발표됐습니다.",
                analysisPort,
                usagePolicy
        );

        var result = useCase.start(ARTICLE_URL.toString(), MEMBER);

        assertThat(result.status()).isEqualTo(HealthAnalysisRoutingStatus.NOT_HEALTH_ARTICLE);
        assertThat(result.userMessage()).contains("건강·의학 기사만 확인할 수 있습니다.");
        assertThat(analysisPort.receivedArticles()).isEmpty();
        assertThat(usagePolicy.failureCount()).isEqualTo(1);
    }

    /** 분야 판단 어려움의 사용자 안내 후 분석 중단 */
    @Test
    void stopsAnalysisWhenHealthTopicIsUncertain() throws Exception {
        var analysisPort = new MockHealthAnalysisPort();
        var usagePolicy = new MockHealthTopicFailureUsagePolicy();
        var useCase = useCase(
                HealthArticleTopicDecision.UNCERTAIN,
                "생활 습관 변화에 관한 전문가 의견",
                "여러 전문가는 생활 습관 변화의 영향을 더 살펴봐야 한다고 설명했습니다.",
                analysisPort,
                usagePolicy
        );

        var result = useCase.start(ARTICLE_URL.toString(), MEMBER);

        assertThat(result.status()).isEqualTo(HealthAnalysisRoutingStatus.TOPIC_UNCERTAIN);
        assertThat(result.userMessage()).contains("건강·의학 기사 여부를 확인하기 어렵습니다.");
        assertThat(analysisPort.receivedArticles()).isEmpty();
        assertThat(usagePolicy.failureCount()).isEqualTo(1);
    }

    /** 이용량 저장소 장애의 기사 수집 전 접수 차단 */
    @Test
    void blocksBeforeScreeningWhenUsagePolicyIsUnavailable() {
        HealthArticleScreeningService screeningService = mock(HealthArticleScreeningService.class);
        HealthAnalysisPort analysisPort = mock(HealthAnalysisPort.class);
        HealthTopicFailureUsagePolicy usagePolicy = new HealthTopicFailureUsagePolicy() {
            @Override
            public HealthTopicFailureUsageResult currentUsage(HealthAnalysisUsageSubject subject) {
                throw new AssertionError("Usage must not be read");
            }

            @Override
            public void verifyCanStart(HealthAnalysisUsageSubject subject) {
                throw new IllegalStateException("Redis unavailable");
            }

            @Override
            public HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject) {
                throw new AssertionError("Failure must not be recorded");
            }

            @Override
            public HealthTopicFailureUsageResult recordAnalysisStart(HealthAnalysisUsageSubject subject) {
                throw new AssertionError("Analysis must not start");
            }
        };
        HealthAnalysisUseCase useCase = new HealthAnalysisUseCase(
                screeningService,
                analysisPort,
                usagePolicy
        );

        assertThatThrownBy(() -> useCase.start(ARTICLE_URL.toString(), MEMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis unavailable");
        verifyNoInteractions(screeningService, analysisPort);
    }

    /** 실제 수집 경계와 판별값별 Use Case 구성 */
    private HealthAnalysisUseCase useCase(
            HealthArticleTopicDecision topicDecision,
            String articleTitle,
            String articleBody,
            HealthAnalysisPort analysisPort,
            HealthTopicFailureUsagePolicy usagePolicy
    ) throws Exception {
        PublisherDomainAccessService accessService = mock(PublisherDomainAccessService.class);
        when(accessService.requireActivePublisherHosts("news.example"))
                .thenReturn(Set.of("news.example"));

        HostResolver resolver = hostname -> List.of(
                InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleUrlValidator validator = new ArticleUrlValidator(resolver);
        ArticleHttpClient httpClient = (target, timeout, maxResponseBytes) ->
                htmlResponse(articleTitle, articleBody);
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
        HealthArticleScreeningService screeningService = new HealthArticleScreeningService(
                articleReader,
                article -> topicDecision
        );
        return new HealthAnalysisUseCase(screeningService, analysisPort, usagePolicy);
    }

    /** 고정 기사 HTML 응답 */
    private ArticleHttpResponse htmlResponse(String title, String body) {
        String html = """
                <html lang="ko"><head>
                <meta property="og:title" content="%s">
                <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
                </head><body><article>
                <p>%s</p>
                </article></body></html>
                """.formatted(title, body);
        return new ArticleHttpResponse(
                200,
                "text/html; charset=UTF-8",
                html,
                html.getBytes(StandardCharsets.UTF_8).length,
                null
        );
    }

    /** 후속 분석 호출 기록 Mock */
    private static final class MockHealthAnalysisPort implements HealthAnalysisPort {

        private final List<ExtractedArticle> receivedArticles = new ArrayList<>();

        /** 정제 기사 분석 시작 기록 */
        @Override
        public HealthAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
            receivedArticles.add(article);
            return MockHealthAnalysisPortFixtures.result(article);
        }

        /** 전달된 정제 기사 목록 */
        private List<ExtractedArticle> receivedArticles() {
            return List.copyOf(receivedArticles);
        }
    }

    /** Mock 분석 결과 Fixture */
    private static final class MockHealthAnalysisPortFixtures {

        private static HealthAnalysisResult result(ExtractedArticle article) {
            return new HealthAnalysisResult(
                    new HealthAnalysisResult.ArticleSummary(
                            article.sourceUrl(),
                            article.title(),
                            article.sourceUrl().getHost(),
                            article.publishedAt(),
                            article.modifiedAt().orElse(null)
                    ),
                    Instant.parse("2026-09-18T01:00:00Z"),
                    HealthAnalysisResult.OverallStatus.CAUTION,
                    java.math.BigDecimal.ZERO.setScale(2),
                    0,
                    1,
                    List.of(new HealthAnalysisResult.Claim(
                            1,
                            article.title(),
                            HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                            "Mock 분석에서는 근거 검색을 수행하지 않습니다.",
                            List.of()
                    )),
                    HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                    "mock-health-analysis-v1",
                    "health-analysis-policy-v1",
                    "evidence-allowlist-v1",
                    false
            );
        }
    }

    /** 이용량 확인과 실패 기록 횟수 Mock */
    private static final class MockHealthTopicFailureUsagePolicy implements HealthTopicFailureUsagePolicy {

        private int verificationCount;
        private int failureCount;
        private int analysisStartCount;

        /** 현재 이용량 조회 */
        @Override
        public HealthTopicFailureUsageResult currentUsage(HealthAnalysisUsageSubject subject) {
            return new HealthTopicFailureUsageResult(false, 0, subject.userType().dailyLimit());
        }

        /** 신규 건강 분석 접수 확인 기록 */
        @Override
        public void verifyCanStart(HealthAnalysisUsageSubject subject) {
            verificationCount++;
        }

        /** 분야 판별 실패 기록 */
        @Override
        public HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject) {
            failureCount++;
            return new HealthTopicFailureUsageResult(failureCount > 1, Math.max(0, failureCount - 1), 5);
        }

        /** 후속 분석 시작 이용량 기록 */
        @Override
        public HealthTopicFailureUsageResult recordAnalysisStart(HealthAnalysisUsageSubject subject) {
            analysisStartCount++;
            return new HealthTopicFailureUsageResult(true, 1, subject.userType().dailyLimit());
        }

        /** 접수 확인 횟수 */
        private int verificationCount() {
            return verificationCount;
        }

        /** 분야 실패 기록 횟수 */
        private int failureCount() {
            return failureCount;
        }

        /** 실제 분석 시작 기록 횟수 */
        private int analysisStartCount() {
            return analysisStartCount;
        }
    }
}
