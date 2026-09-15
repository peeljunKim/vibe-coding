/* 기사 수집 제한과 구성요소 연결 */
package com.newsverification.config;

import com.newsverification.article.application.ArticleHtmlExtractor;
import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.article.application.HostResolver;
import com.newsverification.article.application.SafeArticleReader;
import com.newsverification.article.infrastructure.ApacheArticleHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** 검증된 기사 수집기의 운영 제한 구성 */
@Configuration
public class ArticleExtractionConfig {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    /** URL 보안 검증기 구성 */
    @Bean
    ArticleUrlValidator articleUrlValidator(HostResolver hostResolver) {
        return new ArticleUrlValidator(hostResolver);
    }

    /** 기사 HTML 추출기 구성 */
    @Bean
    ArticleHtmlExtractor articleHtmlExtractor() {
        return new ArticleHtmlExtractor();
    }

    /** 검증 IP 고정 HTTP Client 구성 */
    @Bean
    ArticleHttpClient articleHttpClient() {
        return new ApacheArticleHttpClient();
    }

    /** 기사 수집 제한 적용 */
    @Bean
    SafeArticleReader safeArticleReader(
            ArticleUrlValidator urlValidator,
            ArticleHtmlExtractor htmlExtractor,
            ArticleHttpClient httpClient
    ) {
        return new SafeArticleReader(
                urlValidator,
                htmlExtractor,
                httpClient,
                REQUEST_TIMEOUT,
                MAX_RESPONSE_BYTES,
                MAX_REDIRECTS
        );
    }
}
