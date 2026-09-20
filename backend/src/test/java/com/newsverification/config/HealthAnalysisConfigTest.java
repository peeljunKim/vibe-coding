/* 건강 분석 Mock 구성 연결 검증 */
package com.newsverification.config;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisQueue;
import com.newsverification.health.application.HealthAnalysisUseCase;
import com.newsverification.health.application.HealthAnalysisWorker;
import com.newsverification.health.application.HealthArticleTopicClassifier;
import com.newsverification.health.application.DefaultHealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
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
            .withBean(
                    AnalysisJobOutcomeStore.class,
                    () -> mock(AnalysisJobOutcomeStore.class)
            )
            .withBean(HealthAnalysisQueue.class, () -> mock(HealthAnalysisQueue.class))
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
}
