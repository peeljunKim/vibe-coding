/* 건강 분야 판별 실패 이용량 경계 */
package com.newsverification.health.application;

import com.newsverification.analysiscache.application.AnalysisCacheKey;

import java.util.Optional;

/** 건강 분석 접수 한도 확인과 분야 실패 차감 Port */
public interface HealthTopicFailureUsagePolicy {

    /** 차감 없는 현재 건강 분석 이용량 조회 */
    HealthTopicFailureUsageResult currentUsage(HealthAnalysisUsageSubject subject);

    /** 신규 건강 분석 접수 가능 여부 확인 */
    void verifyCanStart(HealthAnalysisUsageSubject subject);

    /** 분야 판별 실패의 무료 처리 또는 횟수 차감 */
    HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject);

    /** 실제 후속 분석 시작의 이용 횟수 차감 */
    HealthTopicFailureUsageResult recordAnalysisStart(HealthAnalysisUsageSubject subject);

    /** Cache 유효성과 동일 열람자를 포함한 최초 접근 원자 차감 */
    default Optional<HealthTopicFailureUsageResult> recordCacheAccess(
            HealthAnalysisUsageSubject subject,
            AnalysisCacheKey cacheKey,
            String viewerFingerprint
    ) {
        return Optional.empty();
    }
}
