/* 기사 제목 분석 이용량 Port */
package com.newsverification.headline.application;

import com.newsverification.analysiscache.application.AnalysisCacheKey;

import java.util.Optional;

/** 제목 분석 접수 한도와 실제 분석 시작 차감 경계 */
public interface HeadlineAnalysisUsagePolicy {

    /** 차감 없는 현재 제목 분석 이용량 조회 */
    HeadlineAnalysisUsageResult currentUsage(HeadlineAnalysisUsageSubject subject);

    /** 신규 제목 분석 접수 가능 여부 확인 */
    void verifyCanStart(HeadlineAnalysisUsageSubject subject);

    /** 실제 제목 분석 시작의 이용 횟수 차감 */
    HeadlineAnalysisUsageResult recordAnalysisStart(HeadlineAnalysisUsageSubject subject);

    /** Cache 유효성과 동일 열람자를 포함한 최초 접근 원자 차감 */
    default Optional<HeadlineAnalysisCacheUsageResult> recordCacheAccess(
            HeadlineAnalysisUsageSubject subject,
            AnalysisCacheKey cacheKey,
            String viewerFingerprint
    ) {
        return Optional.empty();
    }
}
