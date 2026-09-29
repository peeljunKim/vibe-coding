/* 분석 공용 Cache Version 구성 */
package com.newsverification.config;

import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 모델·판정·출처·언론사 정책 Version 주입 */
@Configuration
public class AnalysisCacheConfig {

    /** Cache 무효화 Version 묶음 */
    @Bean
    AnalysisCacheVersions analysisCacheVersions(
            @Value("${HEALTH_ANALYSIS_MODEL_VERSION:mock-health-analysis-v1}") String healthModelVersion,
            @Value("${HEALTH_ANALYSIS_POLICY_VERSION:health-analysis-policy-v1}") String healthPolicyVersion,
            @Value("${HEALTH_EVIDENCE_ALLOWLIST_VERSION:evidence-allowlist-v1}") String evidenceVersion,
            @Value("${HEADLINE_ANALYSIS_MODEL_VERSION:mock-headline-analysis-v1}") String headlineModelVersion,
            @Value("${HEADLINE_ANALYSIS_POLICY_VERSION:headline-analysis-policy-v1}") String headlinePolicyVersion,
            @Value("${PUBLISHER_POLICY_VERSION:publisher-policy-v1}") String publisherPolicyVersion
    ) {
        return new AnalysisCacheVersions(
                healthModelVersion,
                healthPolicyVersion,
                evidenceVersion,
                headlineModelVersion,
                headlinePolicyVersion,
                publisherPolicyVersion
        );
    }
}
