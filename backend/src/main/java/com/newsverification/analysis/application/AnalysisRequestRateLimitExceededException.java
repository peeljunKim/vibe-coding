/* 분석 접수 요청 제한 예외 */
package com.newsverification.analysis.application;

/** 짧은 시간의 과도한 분석 접수 차단 */
public class AnalysisRequestRateLimitExceededException extends RuntimeException {

    /** 공개 원인 없는 요청 제한 예외 */
    public AnalysisRequestRateLimitExceededException() {
        super("Analysis request rate limit exceeded");
    }
}
