/* 건강 분석 분기 결과 */
package com.newsverification.health.application;

import java.util.Optional;

/** 후속 분석 상태와 사용자 안내 */
public record HealthAnalysisRoutingResult(
        HealthAnalysisRoutingStatus status,
        Optional<String> userMessage
) {
}
