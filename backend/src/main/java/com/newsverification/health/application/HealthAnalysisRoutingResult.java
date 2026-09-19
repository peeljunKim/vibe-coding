/* 건강 분석 분기 결과 */
package com.newsverification.health.application;

import java.util.Optional;

/** 후속 분석 상태와 사용자 안내 */
public record HealthAnalysisRoutingResult(
        HealthAnalysisRoutingStatus status,
        Optional<String> userMessage,
        Optional<HealthAnalysisResult> result
) {

    /** 분석 결과 없는 중단 상태 구성 */
    public HealthAnalysisRoutingResult(
            HealthAnalysisRoutingStatus status,
            Optional<String> userMessage
    ) {
        this(status, userMessage, Optional.empty());
    }
}
