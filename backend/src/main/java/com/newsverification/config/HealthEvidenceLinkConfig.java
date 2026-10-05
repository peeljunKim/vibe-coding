/* 운영 근거 링크 HTTP 확인 구성 */
package com.newsverification.config;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import com.newsverification.health.infrastructure.HttpHealthEvidenceLinkChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** 명시적 허용 Host를 사용하는 운영 근거 링크 Adapter 연결 */
@Configuration
@Profile("evidence-http | pubmed-http")
public class HealthEvidenceLinkConfig {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_BYTES = 1_024;
    private static final int MAX_REDIRECTS = 3;

    /** 운영 근거 링크 상태 확인기 구성 */
    @Bean
    HealthEvidenceLinkChecker httpHealthEvidenceLinkChecker(
            ArticleUrlValidator urlValidator,
            ArticleHttpClient httpClient,
            @Value("${app.analysis.evidence-allowed-hosts:}") String configuredHosts,
            Environment environment
    ) {
        Set<String> allowedHosts = new LinkedHashSet<>();
        Arrays.stream(configuredHosts.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .forEach(allowedHosts::add);
        if (environment.matchesProfiles("pubmed-http")) {
            allowedHosts.add("pubmed.ncbi.nlm.nih.gov");
        }
        return new HttpHealthEvidenceLinkChecker(
                urlValidator,
                httpClient,
                allowedHosts,
                REQUEST_TIMEOUT,
                MAX_RESPONSE_BYTES,
                MAX_REDIRECTS
        );
    }
}
