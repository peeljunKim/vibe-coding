/* 기사 제목 분석 공용 Cache Port */
package com.newsverification.headline.application;

import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.CachedAnalysisResult;

import java.util.Optional;

/** 제목 분석 구조화 결과의 조회·저장 경계 */
public interface HeadlineAnalysisResultCache {

    /** TTL 연장 없는 제목 결과 조회 */
    Optional<CachedAnalysisResult<HeadlineAnalysisResult>> findHeadline(AnalysisCacheKey key);

    /** 원 분석 열람자를 포함한 제목 결과 저장 */
    CachedAnalysisResult<HeadlineAnalysisResult> saveHeadline(
            AnalysisCacheKey key,
            HeadlineAnalysisResult result,
            String viewerFingerprint
    );

    /** Cache 미사용 단위 테스트 기본값 */
    static HeadlineAnalysisResultCache disabled() {
        return new HeadlineAnalysisResultCache() {
            @Override
            public Optional<CachedAnalysisResult<HeadlineAnalysisResult>> findHeadline(AnalysisCacheKey key) {
                return Optional.empty();
            }

            @Override
            public CachedAnalysisResult<HeadlineAnalysisResult> saveHeadline(
                    AnalysisCacheKey key,
                    HeadlineAnalysisResult result,
                    String viewerFingerprint
            ) {
                return new CachedAnalysisResult<>(result, java.time.Instant.MAX);
            }
        };
    }
}
