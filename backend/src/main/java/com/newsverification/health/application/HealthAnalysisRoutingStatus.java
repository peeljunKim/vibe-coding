/* 건강 분석 분기 상태 */
package com.newsverification.health.application;

/** 분야 판별 이후 진행 상태 */
public enum HealthAnalysisRoutingStatus {
    ANALYSIS_STARTED,
    NOT_HEALTH_ARTICLE,
    TOPIC_UNCERTAIN
}
