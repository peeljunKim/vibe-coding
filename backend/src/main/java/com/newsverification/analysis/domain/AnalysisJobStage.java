/* 비동기 분석 진행 단계 */
package com.newsverification.analysis.domain;

/** 분석 작업 진행 단계 */
public enum AnalysisJobStage {
    QUEUED,
    CHECKING_ARTICLE,
    SEARCHING_EVIDENCE,
    GENERATING_RESULT,
    COMPLETED,
    FAILED
}
