/* 기사 제목 분석 서비스 장애 예외 */
package com.newsverification.headline.application;

/** Queue 포화와 Redis 장애의 Application 예외 */
public class HeadlineAnalysisServiceUnavailableException extends RuntimeException {

    public HeadlineAnalysisServiceUnavailableException() {
        super("Headline analysis service is unavailable");
    }

    public HeadlineAnalysisServiceUnavailableException(Throwable cause) {
        super("Headline analysis service is unavailable", cause);
    }
}
