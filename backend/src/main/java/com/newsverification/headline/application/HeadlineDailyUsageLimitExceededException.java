/* 기사 제목 분석 일일 한도 초과 예외 */
package com.newsverification.headline.application;

/** 제목 분석 일일 횟수 소진 상태 */
public class HeadlineDailyUsageLimitExceededException extends RuntimeException {

    private final int usedCount;
    private final int dailyLimit;

    /** 현재 이용량과 일일 한도 보관 */
    public HeadlineDailyUsageLimitExceededException(int usedCount, int dailyLimit) {
        super("Headline analysis daily usage limit exceeded");
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
