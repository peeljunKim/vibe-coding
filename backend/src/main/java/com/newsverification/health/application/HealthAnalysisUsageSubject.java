/* 건강 분석 이용량 식별 입력 */
package com.newsverification.health.application;

import java.util.List;
import java.util.Objects;

/** 외부에서 생성된 회원·비회원 비식별 Key 묶음 */
public record HealthAnalysisUsageSubject(
        HealthAnalysisUserType userType,
        List<String> identifierKeys
) {

    /** 이용자 유형과 식별 Key 묶음 검증 */
    public HealthAnalysisUsageSubject {
        Objects.requireNonNull(userType);
        if (identifierKeys == null || identifierKeys.isEmpty()) {
            throw new IllegalArgumentException("Health analysis identifier keys are required");
        }
        if (identifierKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Health analysis identifier key cannot be blank");
        }
        identifierKeys = List.copyOf(identifierKeys);
    }

    /** 단일 외부 식별 Key 호환 구성 */
    public HealthAnalysisUsageSubject(HealthAnalysisUserType userType, String identifierKey) {
        this(userType, List.of(identifierKey));
    }
}
