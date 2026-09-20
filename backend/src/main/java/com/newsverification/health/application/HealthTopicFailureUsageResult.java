/* 건강 분야 판별 실패 이용량 결과 */
package com.newsverification.health.application;

/** 무료 여부와 현재 건강 분석 이용량 */
public record HealthTopicFailureUsageResult(
        boolean charged,
        int usedCount,
        int dailyLimit
) {
}
