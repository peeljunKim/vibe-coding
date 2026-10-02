/* 분석 접수 요청 제한 Port */
package com.newsverification.analysis.application;

import java.util.List;

/** 기능별 비식별 요청 횟수 제한 경계 */
public interface AnalysisRequestRateLimiter {

    /** 기능과 비식별 식별값의 요청 접수 기록 */
    void acquire(Feature feature, List<String> identifierKeys);

    /** 요청 제한 미사용 테스트 기본값 */
    static AnalysisRequestRateLimiter unlimited() {
        return (feature, identifierKeys) -> {
        };
    }

    /** 독립 요청 제한 기능 */
    enum Feature {
        HEALTH,
        HEADLINE
    }
}
