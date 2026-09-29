/* 분석 공용 Cache Version 정책 */
package com.newsverification.analysiscache.application;

/** 기능별 모델·판정·출처·언론사 정책 Version */
public record AnalysisCacheVersions(
        String healthModelVersion,
        String healthPolicyVersion,
        String healthEvidenceAllowlistVersion,
        String headlineModelVersion,
        String headlinePolicyVersion,
        String publisherPolicyVersion
) {

    /** 모든 Cache 무효화 Version의 명시적 값 검증 */
    public AnalysisCacheVersions {
        healthModelVersion = requireVersion(healthModelVersion, "Health model version");
        healthPolicyVersion = requireVersion(healthPolicyVersion, "Health policy version");
        healthEvidenceAllowlistVersion = requireVersion(
                healthEvidenceAllowlistVersion,
                "Health evidence allowlist version"
        );
        headlineModelVersion = requireVersion(headlineModelVersion, "Headline model version");
        headlinePolicyVersion = requireVersion(headlinePolicyVersion, "Headline policy version");
        publisherPolicyVersion = requireVersion(publisherPolicyVersion, "Publisher policy version");
    }

    /** 현재 Local Mock과 정책 Version */
    public static AnalysisCacheVersions mockDefaults() {
        return new AnalysisCacheVersions(
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                "mock-headline-analysis-v1",
                "headline-analysis-policy-v1",
                "publisher-policy-v1"
        );
    }

    /** 건강 결과 Version의 현재 Cache 정책 일치 확인 */
    public boolean matchesHealth(String modelVersion, String policyVersion, String evidenceVersion) {
        return healthModelVersion.equals(modelVersion)
                && healthPolicyVersion.equals(policyVersion)
                && healthEvidenceAllowlistVersion.equals(evidenceVersion);
    }

    /** 제목 결과 Version의 현재 Cache 정책 일치 확인 */
    public boolean matchesHeadline(String modelVersion, String policyVersion) {
        return headlineModelVersion.equals(modelVersion)
                && headlinePolicyVersion.equals(policyVersion);
    }

    /** 공백 없는 Version 문자열 확인 */
    private static String requireVersion(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
