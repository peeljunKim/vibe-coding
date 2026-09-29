/* Redis 분석 공용 Cache Key 규칙 */
package com.newsverification.analysiscache.infrastructure;

import com.newsverification.analysiscache.application.AnalysisCacheFeature;
import com.newsverification.analysiscache.application.AnalysisCacheKey;

import java.util.Locale;

/** Cache 결과와 비식별 열람 Marker Namespace */
public final class RedisAnalysisCacheKeys {

    private RedisAnalysisCacheKeys() {
    }

    /** 기능별 결과 Hash Key */
    public static String cacheKey(String keyPrefix, AnalysisCacheKey key) {
        return basePrefix(keyPrefix) + feature(key.feature()) + ":" + key.id();
    }

    /** 기능별 비식별 열람 Marker Key */
    public static String viewerKey(
            String keyPrefix,
            AnalysisCacheFeature feature,
            String cacheId,
            String viewerFingerprint
    ) {
        requirePrefix(keyPrefix);
        if (cacheId == null || !cacheId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Analysis cache id is required");
        }
        if (viewerFingerprint == null || !viewerFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Cache viewer fingerprint is required");
        }
        return keyPrefix + ":analysis-cache-view:v1:" + feature(feature)
                + ":" + cacheId + ":" + viewerFingerprint;
    }

    /** 결과 Namespace Prefix */
    private static String basePrefix(String keyPrefix) {
        requirePrefix(keyPrefix);
        return keyPrefix + ":analysis-cache:v1:";
    }

    /** 소문자 기능 Namespace */
    private static String feature(AnalysisCacheFeature feature) {
        if (feature == null) {
            throw new IllegalArgumentException("Analysis cache feature is required");
        }
        return feature.name().toLowerCase(Locale.ROOT);
    }

    /** 환경별 Prefix 검증 */
    private static void requirePrefix(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
    }
}
