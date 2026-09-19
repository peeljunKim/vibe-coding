/* 기사 제목 분석 Queue 작업 모델 */
package com.newsverification.headline.application;

import java.util.List;

/** 원문 본문 없는 Worker 입력 */
public record HeadlineAnalysisTask(
        String analysisId,
        String articleUrl,
        HeadlineAnalysisUserType userType,
        List<String> usageIdentifierKeys
) {

    /** Queue 필수값과 비식별 이용량 Key 검증 */
    public HeadlineAnalysisTask {
        if (analysisId == null || analysisId.isBlank()) {
            throw new IllegalArgumentException("Analysis id is required");
        }
        if (articleUrl == null || articleUrl.isBlank()) {
            throw new IllegalArgumentException("Article URL is required");
        }
        if (userType == null || usageIdentifierKeys == null || usageIdentifierKeys.isEmpty()
                || usageIdentifierKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Headline usage identity is required");
        }
        usageIdentifierKeys = List.copyOf(usageIdentifierKeys);
    }

    /** Worker 이용량 입력 변환 */
    public HeadlineAnalysisUsageSubject usageSubject() {
        return new HeadlineAnalysisUsageSubject(userType, usageIdentifierKeys);
    }
}
