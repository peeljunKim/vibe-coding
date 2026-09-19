/* 기사 제목 분석 Mock 실행 구성 */
package com.newsverification.config;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.headline.application.HeadlineAnalysisJobIdentityService;
import com.newsverification.headline.application.HeadlineAnalysisPort;
import com.newsverification.headline.application.HeadlineAnalysisUseCase;
import com.newsverification.headline.infrastructure.MockHeadlineAnalysisPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;

/** 외부 호출 없는 제목 분석 흐름 연결 */
@Configuration
public class HeadlineAnalysisConfig {

    /** 회원·비회원 제목 작업 소유권 구성 */
    @Bean
    HeadlineAnalysisJobIdentityService headlineAnalysisJobIdentityService(
            Clock clock,
            @Value("${app.time-zone}") ZoneId usageZone,
            @Value("${app.analysis.lookup-hmac-key}") String lookupHmacKey,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie
    ) {
        return new HeadlineAnalysisJobIdentityService(
                clock,
                usageZone,
                lookupHmacKey.getBytes(StandardCharsets.UTF_8),
                new SecureRandom(),
                secureCookie
        );
    }

    /** Local 기사 제목 분석 Mock */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.analysis",
            name = "provider",
            havingValue = "mock",
            matchIfMissing = true
    )
    HeadlineAnalysisPort headlineAnalysisPort(Clock clock) {
        return new MockHeadlineAnalysisPort(clock);
    }

    /** 기사 수집과 제목·본문 비교 흐름 */
    @Bean
    HeadlineAnalysisUseCase headlineAnalysisUseCase(
            PublisherArticleReader articleReader,
            HeadlineAnalysisPort analysisPort
    ) {
        return new HeadlineAnalysisUseCase(articleReader, analysisPort);
    }
}
