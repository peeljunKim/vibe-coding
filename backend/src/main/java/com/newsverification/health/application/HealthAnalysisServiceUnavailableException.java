/* 건강 분석 접수 불가 예외 */
package com.newsverification.health.application;

/** Queue 포화와 Redis 장애의 공통 Application 오류 */
public class HealthAnalysisServiceUnavailableException extends RuntimeException {

    /** 비민감 공통 메시지 구성 */
    public HealthAnalysisServiceUnavailableException() {
        super("Health analysis service is unavailable");
    }

    /** 내부 원인 보존 구성 */
    public HealthAnalysisServiceUnavailableException(Throwable cause) {
        super("Health analysis service is unavailable", cause);
    }
}
