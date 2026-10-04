/* 일일 이용량 저장소 장애 예외 */
package com.newsverification.usage.application;

/** Redis 이용량 조회 불가 상태 */
public class DailyUsageServiceUnavailableException extends RuntimeException {

    /** 원인 없는 조회 장애 */
    public DailyUsageServiceUnavailableException() {
        super("Daily usage service is unavailable");
    }

    /** 내부 원인을 숨기는 조회 장애 */
    public DailyUsageServiceUnavailableException(Throwable cause) {
        super("Daily usage service is unavailable", cause);
    }
}
