/* 분석 작업 소유권 모델 */
package com.newsverification.analysis.domain;

import java.util.Objects;

/** 외부 원문을 포함하지 않는 비식별 소유권 Key */
public record AnalysisJobOwner(
        AnalysisJobOwnerType type,
        String accessKeyHash
) {

    /** 소유자 유형과 비식별 Key 검증 */
    public AnalysisJobOwner {
        Objects.requireNonNull(type, "Analysis job owner type is required");
        if (accessKeyHash == null || accessKeyHash.isBlank()) {
            throw new IllegalArgumentException("Analysis job owner access key is required");
        }
        accessKeyHash = accessKeyHash.trim();
    }
}
