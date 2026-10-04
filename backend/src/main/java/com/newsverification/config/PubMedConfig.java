/* 운영 PubMed E-utilities 연결 구성 */
package com.newsverification.config;

import com.newsverification.health.application.PubMedEvidenceSearchPort;
import com.newsverification.health.infrastructure.HttpPubMedEvidenceSearchAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** 명시적 Profile과 NCBI 연락처를 사용하는 PubMed Adapter 연결 */
@Configuration
@Profile("pubmed-http")
public class PubMedConfig {

    /** 실제 NCBI E-utilities 검색 Adapter 구성 */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.analysis",
            name = "provider",
            havingValue = "gemini"
    )
    PubMedEvidenceSearchPort httpPubMedEvidenceSearchPort(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${PUBMED_CONTACT_EMAIL:}") String contactEmail,
            @Value("${PUBMED_API_KEY:}") String apiKey
    ) {
        return new HttpPubMedEvidenceSearchAdapter(
                objectMapper,
                clock,
                contactEmail,
                apiKey
        );
    }
}
