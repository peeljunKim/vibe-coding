/* 건강 분석 공용 Cache Port */
package com.newsverification.health.application;

import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.CachedAnalysisResult;

import java.util.Optional;

/** 건강 분석 구조화 결과의 조회·저장 경계 */
public interface HealthAnalysisResultCache {

    /** TTL 연장 없는 건강 결과 조회 */
    Optional<CachedAnalysisResult<HealthAnalysisResult>> findHealth(AnalysisCacheKey key);

    /** 원 분석 열람자를 포함한 건강 결과 저장 */
    CachedAnalysisResult<HealthAnalysisResult> saveHealth(
            AnalysisCacheKey key,
            HealthAnalysisResult result,
            String viewerFingerprint
    );

    /** Cache 미사용 단위 테스트 기본값 */
    static HealthAnalysisResultCache disabled() {
        return new HealthAnalysisResultCache() {
            @Override
            public Optional<CachedAnalysisResult<HealthAnalysisResult>> findHealth(AnalysisCacheKey key) {
                return Optional.empty();
            }

            @Override
            public CachedAnalysisResult<HealthAnalysisResult> saveHealth(
                    AnalysisCacheKey key,
                    HealthAnalysisResult result,
                    String viewerFingerprint
            ) {
                return new CachedAnalysisResult<>(result, java.time.Instant.MAX);
            }
        };
    }
}
