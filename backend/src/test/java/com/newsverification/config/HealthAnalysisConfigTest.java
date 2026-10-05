/* 건강 분석 Mock 구성 연결 검증 */
package com.newsverification.config;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisRequestRateLimiter;
import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import com.newsverification.health.application.HealthEvidenceLinkValidationService;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisQueue;
import com.newsverification.health.application.HealthAnalysisUseCase;
import com.newsverification.health.application.HealthAnalysisWorker;
import com.newsverification.health.application.HealthArticleTopicClassifier;
import com.newsverification.health.application.DefaultHealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResultCache;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import com.newsverification.health.application.PubMedEvidenceSearchService;
import com.newsverification.health.infrastructure.HttpHealthEvidenceLinkChecker;
import com.newsverification.health.infrastructure.HttpPubMedEvidenceSearchAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetAddress;
import java.time.Clock;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 외부 호출 없는 건강 분석 Bean 조립 */
class HealthAnalysisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    HealthAnalysisConfig.class,
                    DefaultHealthAnalysisJobService.class,
                    HealthAnalysisWorker.class
            )
            .withBean(AnalysisJobStore.class, () -> mock(AnalysisJobStore.class))
            .withBean(AnalysisRequestRateLimiter.class, AnalysisRequestRateLimiter::unlimited)
            .withBean(
                    AnalysisJobOutcomeStore.class,
                    () -> mock(AnalysisJobOutcomeStore.class)
            )
            .withBean(HealthAnalysisQueue.class, () -> mock(HealthAnalysisQueue.class))
            .withBean(HealthAnalysisResultCache.class, HealthAnalysisResultCache::disabled)
            .withBean(AnalysisCacheVersions.class, AnalysisCacheVersions::mockDefaults)
            .withBean(PublisherArticleReader.class, () -> mock(PublisherArticleReader.class))
            .withBean(
                    HealthTopicFailureUsagePolicy.class,
                    () -> mock(HealthTopicFailureUsagePolicy.class)
            )
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "app.time-zone=Asia/Seoul",
                    "app.analysis.lookup-hmac-key=test-only-lookup-hmac-key",
                    "app.analysis.provider=mock",
                    "server.servlet.session.cookie.secure=false"
            );

    /** Mock Port와 작업 소유권 구성 생성 */
    @Test
    void wiresMockAnalysisWithoutExternalProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Clock.class);
            assertThat(context).hasSingleBean(HealthArticleTopicClassifier.class);
            assertThat(context).hasSingleBean(HealthAnalysisPort.class);
            assertThat(context).hasSingleBean(HealthEvidenceLinkChecker.class);
            assertThat(context).hasSingleBean(HealthEvidenceLinkValidationService.class);
            assertThat(context).hasSingleBean(PubMedEvidenceSearchPort.class);
            assertThat(context).hasSingleBean(PubMedEvidenceSearchService.class);
            assertThat(context).hasSingleBean(HealthAnalysisJobIdentityService.class);
            assertThat(context).hasSingleBean(HealthAnalysisUseCase.class);
            assertThat(context).hasSingleBean(HealthAnalysisJobService.class);
            assertThat(context).hasSingleBean(HealthAnalysisWorker.class);
        });
    }

    /** 분석 Provider 누락 시 안전한 시작 실패 */
    @Test
    void failsWhenAnalysisProviderIsNotExplicitlyConfigured() {
        contextRunner
                .withPropertyValues("app.analysis.provider=")
                .run(context -> assertThat(context).hasFailed());
    }

    /** 운영 Profile의 HTTP 근거 링크 Adapter 교체 */
    @Test
    void wiresHttpEvidenceCheckerOnlyWithExplicitAllowedHosts() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        contextRunner
                .withUserConfiguration(HealthEvidenceLinkConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("evidence-http"))
                .withBean(
                        ArticleUrlValidator.class,
                        () -> new ArticleUrlValidator(hostname -> List.of(publicAddress))
                )
                .withBean(
                        ArticleHttpClient.class,
                        () -> (target, timeout, maxResponseBytes) ->
                                new ArticleHttpResponse(200, "text/html", "", 0, null)
                )
                .withPropertyValues("app.analysis.evidence-allowed-hosts=evidence.example")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HealthEvidenceLinkChecker.class);
                    assertThat(context.getBean(HealthEvidenceLinkChecker.class))
                            .isInstanceOf(HttpHealthEvidenceLinkChecker.class);
                });
    }

    /** PubMed Profile의 필수 근거 링크 확인기 자동 구성 */
    @Test
    void wiresHttpEvidenceCheckerForPubMedProfile() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        contextRunner
                .withUserConfiguration(HealthEvidenceLinkConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("pubmed-http"))
                .withBean(
                        ArticleUrlValidator.class,
                        () -> new ArticleUrlValidator(hostname -> List.of(publicAddress))
                )
                .withBean(
                        ArticleHttpClient.class,
                        () -> (target, timeout, maxResponseBytes) ->
                                new ArticleHttpResponse(200, "text/html", "", 0, null)
                )
                .withBean(
                        PubMedEvidenceSearchPort.class,
                        () -> request -> PubMedEvidenceSearchPort.SearchResponse.noResults()
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HealthEvidenceLinkChecker.class);
                    assertThat(context.getBean(HealthEvidenceLinkChecker.class))
                            .isInstanceOf(HttpHealthEvidenceLinkChecker.class);
                    assertThat(context).hasSingleBean(PubMedEvidenceSearchService.class);
                });
    }

    /** 운영 Profile의 빈 허용 Host 설정 차단 */
    @Test
    void failsHttpEvidenceProfileWithoutAllowedHosts() {
        contextRunner
                .withUserConfiguration(HealthEvidenceLinkConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("evidence-http"))
                .withBean(ArticleUrlValidator.class, () -> mock(ArticleUrlValidator.class))
                .withBean(ArticleHttpClient.class, () -> mock(ArticleHttpClient.class))
                .run(context -> assertThat(context).hasFailed());
    }

    /** 운영 Profile의 PubMed HTTP Adapter 교체 */
    @Test
    void wiresHttpPubMedAdapterWithContactEmail() {
        pubMedContextRunner()
                .withUserConfiguration(PubMedConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("pubmed-http"))
                .withPropertyValues(
                        "app.analysis.provider=gemini",
                        "PUBMED_CONTACT_EMAIL=developer@example.com"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PubMedEvidenceSearchPort.class);
                    assertThat(context.getBean(PubMedEvidenceSearchPort.class))
                            .isInstanceOf(HttpPubMedEvidenceSearchAdapter.class);
                });
    }

    /** 운영 PubMed Profile의 연락처 누락 차단 */
    @Test
    void failsHttpPubMedProfileWithoutContactEmail() {
        pubMedContextRunner()
                .withUserConfiguration(PubMedConfig.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("pubmed-http"))
                .withPropertyValues("app.analysis.provider=gemini")
                .run(context -> assertThat(context).hasFailed());
    }

    /** PubMed 구성 전용 최소 Context */
    private static ApplicationContextRunner pubMedContextRunner() {
        return new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(Clock.class, Clock::systemUTC);
    }
}
