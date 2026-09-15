/* 건강 분석 이용량 식별 입력 */
package com.newsverification.health.application;

import java.util.Objects;

/** 외부에서 생성된 회원·비회원 식별 Key */
public record HealthAnalysisUsageSubject(
        HealthAnalysisUserType userType,
        String identifierKey
) {

    /** 이용자 유형과 식별 Key 검증 */
    public HealthAnalysisUsageSubject {
        Objects.requireNonNull(userType);
        if (identifierKey == null || identifierKey.isBlank()) {
            throw new IllegalArgumentException("Health analysis identifier key is required");
        }
    }
}
