/* 분석 공용 Cache 식별자 */
package com.newsverification.analysiscache.application;

import java.util.Objects;

/** 기능 Namespace와 비가역 URL·Version Digest */
public record AnalysisCacheKey(AnalysisCacheFeature feature, String id) {

    /** 필수 기능과 SHA-256 식별자 검증 */
    public AnalysisCacheKey {
        Objects.requireNonNull(feature);
        if (id == null || !id.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Analysis cache id must be a SHA-256 hex digest");
        }
    }
}
