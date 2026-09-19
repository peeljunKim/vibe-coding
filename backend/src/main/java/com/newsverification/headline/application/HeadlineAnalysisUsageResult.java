/* 기사 제목 분석 이용량 결과 */
package com.newsverification.headline.application;

/** 차감 후 현재 횟수와 일일 한도 */
public record HeadlineAnalysisUsageResult(int usedCount, int dailyLimit) {
}
