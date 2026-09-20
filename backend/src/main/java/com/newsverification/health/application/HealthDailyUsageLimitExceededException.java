/* 건강 분석 일일 한도 초과 예외 */
package com.newsverification.health.application;

/** 건강 분석 일일 횟수 소진 상태 */
public class HealthDailyUsageLimitExceededException extends RuntimeException {

    private final int usedCount;
    private final int dailyLimit;

    /** 현재 이용량과 일일 한도 보관 */
    public HealthDailyUsageLimitExceededException(int usedCount, int dailyLimit) {
        super("Health analysis daily usage limit exceeded");
        this.usedCount = usedCount;
        this.dailyLimit = dailyLimit;
    }

    /** 현재 차감 횟수 */
    public int usedCount() {
        return usedCount;
    }

    /** 일일 허용 횟수 */
    public int dailyLimit() {
        return dailyLimit;
    }
}
