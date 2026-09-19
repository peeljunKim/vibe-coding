/* 건강 분야 판별 실패 이용량 경계 */
package com.newsverification.health.application;

/** 건강 분석 접수 한도 확인과 분야 실패 차감 Port */
public interface HealthTopicFailureUsagePolicy {

    /** 신규 건강 분석 접수 가능 여부 확인 */
    void verifyCanStart(HealthAnalysisUsageSubject subject);

    /** 분야 판별 실패의 무료 처리 또는 횟수 차감 */
    HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject);

    /** 실제 후속 분석 시작의 이용 횟수 차감 */
    HealthTopicFailureUsageResult recordAnalysisStart(HealthAnalysisUsageSubject subject);
}
