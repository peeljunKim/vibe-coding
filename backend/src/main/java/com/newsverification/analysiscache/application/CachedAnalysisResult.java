/* 분석 공용 Cache 조회 결과 */
package com.newsverification.analysiscache.application;

import java.time.Instant;
import java.util.Objects;

/** 구조화 분석 결과와 Cache 절대 만료 시각 */
public record CachedAnalysisResult<T>(T result, Instant expiresAt) {

    /** 결과와 만료 시각 필수값 검증 */
    public CachedAnalysisResult {
        Objects.requireNonNull(result);
        Objects.requireNonNull(expiresAt);
    }
}
