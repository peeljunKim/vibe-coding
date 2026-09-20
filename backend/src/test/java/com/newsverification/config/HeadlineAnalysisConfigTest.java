/* 기사 제목 분석 Mock 구성 연결 검증 */
package com.newsverification.config;

import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.headline.application.DefaultHeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisJobIdentityService;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisPort;
import com.newsverification.headline.application.HeadlineAnalysisQueue;
import com.newsverification.headline.application.HeadlineAnalysisUsagePolicy;
import com.newsverification.headline.application.HeadlineAnalysisUseCase;
import com.newsverification.headline.application.HeadlineAnalysisWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 외부 호출 없는 제목 분석 Bean 조립 */
class HeadlineAnalysisConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    HeadlineAnalysisConfig.class,
                    DefaultHeadlineAnalysisJobService.class,
                    HeadlineAnalysisWorker.class
            )
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(
                    AnalysisJobLifecycleService.class,
                    () -> mock(AnalysisJobLifecycleService.class)
            )
            .withBean(AnalysisJobStore.class, () -> mock(AnalysisJobStore.class))
            .withBean(AnalysisJobOutcomeStore.class, () -> mock(AnalysisJobOutcomeStore.class))
            .withBean(HeadlineAnalysisQueue.class, () -> mock(HeadlineAnalysisQueue.class))
            .withBean(HeadlineAnalysisUsagePolicy.class, () -> mock(HeadlineAnalysisUsagePolicy.class))
            .withBean(PublisherArticleReader.class, () -> mock(PublisherArticleReader.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "app.time-zone=Asia/Seoul",
                    "app.analysis.lookup-hmac-key=test-only-lookup-hmac-key",
                    "app.analysis.provider=mock",
                    "server.servlet.session.cookie.secure=false"
            );

    /** Mock Port와 제목 작업 소유권 구성 생성 */
    @Test
    void wiresMockHeadlineAnalysisWithoutExternalProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(HeadlineAnalysisPort.class);
            assertThat(context).hasSingleBean(HeadlineAnalysisJobIdentityService.class);
            assertThat(context).hasSingleBean(HeadlineAnalysisUseCase.class);
            assertThat(context).hasSingleBean(HeadlineAnalysisJobService.class);
            assertThat(context).hasSingleBean(HeadlineAnalysisWorker.class);
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
