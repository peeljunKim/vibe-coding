/* 명시적 Gemini 건강 분석 실행 구성 */
package com.newsverification.config;

import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthArticleTopicClassifier;
import com.newsverification.health.application.PubMedEvidenceSearchService;
import com.newsverification.health.infrastructure.HttpGeminiHealthAnalysisAdapter;
import com.newsverification.health.infrastructure.MockHealthArticleTopicClassifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** Profile과 Provider를 모두 요구하는 Gemini Adapter 연결 */
@Configuration
@Profile("gemini")
@ConditionalOnProperty(
        prefix = "app.analysis",
        name = "provider",
        havingValue = "gemini"
)
public class GeminiHealthAnalysisConfig {

    /** 외부 분석 전 Local 기사 분야 1차 판별 */
    @Bean
    HealthArticleTopicClassifier geminiHealthArticleTopicClassifier() {
        return new MockHealthArticleTopicClassifier();
    }

    /** 실제 Gemini 건강 분석 Adapter 구성 */
    @Bean
    HealthAnalysisPort geminiHealthAnalysisPort(
            ObjectMapper objectMapper,
            Clock clock,
            PubMedEvidenceSearchService pubMedSearchService,
            AnalysisCacheVersions cacheVersions,
            @Value("${AI_MODEL:}") String model,
            @Value("${AI_API_KEY:}") String apiKey
    ) {
        String normalizedModel = model == null ? "" : model.trim();
        if (!cacheVersions.healthModelVersion().equals(normalizedModel)) {
            throw new IllegalStateException(
                    "Gemini model and health cache model version must match"
            );
        }
        return new HttpGeminiHealthAnalysisAdapter(
                objectMapper,
                clock,
                pubMedSearchService,
                normalizedModel,
                apiKey,
                cacheVersions.healthPolicyVersion(),
                cacheVersions.healthEvidenceAllowlistVersion()
        );
    }
}
