/* 건강 분석 Mock 실행 구성 */
package com.newsverification.config;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisUseCase;
import com.newsverification.health.application.HealthArticleScreeningService;
import com.newsverification.health.application.HealthArticleTopicClassifier;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import com.newsverification.health.infrastructure.MockHealthAnalysisPort;
import com.newsverification.health.infrastructure.MockHealthArticleTopicClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;

/** Redis Queue와 외부 호출 없는 건강 분석 흐름 연결 */
@EnableScheduling
@Configuration
public class HealthAnalysisConfig {

    /** 공통 UTC 업무 시각 */
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    /** 분석 작업 상태 수명 관리 */
    @Bean
    AnalysisJobLifecycleService analysisJobLifecycleService(
            AnalysisJobStore jobStore,
            Clock clock
    ) {
        return new AnalysisJobLifecycleService(jobStore, clock);
    }

    /** 회원·비회원 작업 소유권 구성 */
    @Bean
    HealthAnalysisJobIdentityService healthAnalysisJobIdentityService(
            Clock clock,
            @Value("${app.time-zone}") ZoneId usageZone,
            @Value("${app.analysis.lookup-hmac-key}") String lookupHmacKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie
    ) {
        return new HealthAnalysisJobIdentityService(
                clock,
                usageZone,
                lookupHmacKey.getBytes(StandardCharsets.UTF_8),
                new SecureRandom(),
                secureCookie
        );
    }

    /** Local 기사 분야 판별 Mock */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.analysis",
            name = "provider",
            havingValue = "mock"
    )
    HealthArticleTopicClassifier healthArticleTopicClassifier() {
        return new MockHealthArticleTopicClassifier();
    }

    /** Local 건강 분석 결과 Mock */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.analysis",
            name = "provider",
            havingValue = "mock"
    )
    HealthAnalysisPort healthAnalysisPort(Clock clock) {
        return new MockHealthAnalysisPort(clock);
    }

    /** 안전 수집 이후 기사 분야 판별 */
    @Bean
    HealthArticleScreeningService healthArticleScreeningService(
            PublisherArticleReader articleReader,
            HealthArticleTopicClassifier topicClassifier
    ) {
        return new HealthArticleScreeningService(articleReader, topicClassifier);
    }

    /** 분야 판별과 구조화 분석 흐름 */
    @Bean
    HealthAnalysisUseCase healthAnalysisUseCase(
            HealthArticleScreeningService screeningService,
            HealthAnalysisPort analysisPort,
            HealthTopicFailureUsagePolicy usagePolicy
    ) {
        return new HealthAnalysisUseCase(screeningService, analysisPort, usagePolicy);
    }
}
