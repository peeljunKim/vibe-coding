/* 기사 제목 Cache 이용량 결과 */
package com.newsverification.headline.application;

/** Cache 최초 열람 차감 여부와 현재 제목 이용량 */
public record HeadlineAnalysisCacheUsageResult(
        boolean charged,
        int usedCount,
        int dailyLimit
) {
}
