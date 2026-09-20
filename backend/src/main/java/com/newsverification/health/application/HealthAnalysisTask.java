/* 건강 분석 Queue 작업 모델 */
package com.newsverification.health.application;

import java.util.List;

/** 원문 개인정보 없는 Worker 입력 */
public record HealthAnalysisTask(
        String analysisId,
        String articleUrl,
        HealthAnalysisUserType userType,
        List<String> usageIdentifierKeys
) {

    /** 필수 Queue 값과 비식별 이용량 Key 검증 */
    public HealthAnalysisTask {
        if (analysisId == null || analysisId.isBlank()) {
            throw new IllegalArgumentException("Analysis id is required");
        }
        if (articleUrl == null || articleUrl.isBlank()) {
            throw new IllegalArgumentException("Article URL is required");
        }
        if (userType == null) {
            throw new IllegalArgumentException("Health analysis user type is required");
        }
        if (usageIdentifierKeys == null || usageIdentifierKeys.isEmpty()
                || usageIdentifierKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Usage identifier keys are required");
        }
        usageIdentifierKeys = List.copyOf(usageIdentifierKeys);
    }

    /** Worker 이용량 식별 입력 변환 */
    public HealthAnalysisUsageSubject usageSubject() {
        return new HealthAnalysisUsageSubject(userType, usageIdentifierKeys);
    }
}
