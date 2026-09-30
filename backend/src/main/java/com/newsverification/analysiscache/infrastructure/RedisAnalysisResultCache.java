/* Redis 분석 공용 Cache Adapter */
package com.newsverification.analysiscache.infrastructure;

import com.newsverification.analysiscache.application.AnalysisCacheFeature;
import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.ArticleRevisionFingerprint;
import com.newsverification.analysiscache.application.CachedAnalysisResult;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthAnalysisResultCache;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import com.newsverification.headline.application.HeadlineAnalysisResultCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 기능별 Redis Hash와 최대 30분 결정적 TTL 지터 */
@Repository
public class RedisAnalysisResultCache
        implements HealthAnalysisResultCache, HeadlineAnalysisResultCache {

    private static final String RESULT_JSON = "resultJson";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String ARTICLE_FINGERPRINT_JSON = "articleFingerprintJson";
    private static final long MAX_JITTER_SECONDS = Duration.ofMinutes(30).toSeconds();
    private static final RedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1],
                'resultJson', ARGV[1],
                'expiresAt', ARGV[2],
                'articleFingerprintJson', ARGV[3])
            redis.call('PEXPIREAT', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], '1')
            redis.call('PEXPIREAT', KEYS[2], ARGV[4])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration baseTtl;
    private final Clock clock;

    /** Redis 연결과 환경별 Namespace·기본 TTL 구성 */
    @Autowired
    public RedisAnalysisResultCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.redis.key-prefix}") String keyPrefix,
            @Value("${app.redis.analysis-cache-ttl:3d}") Duration baseTtl
    ) {
        this(redisTemplate, objectMapper, keyPrefix, baseTtl, Clock.systemUTC());
    }

    /** 테스트 제어용 시계 포함 구성 */
    RedisAnalysisResultCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            String keyPrefix,
            Duration baseTtl,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper)
                .rebuild()
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        if (baseTtl == null || baseTtl.compareTo(Duration.ofMinutes(30)) <= 0) {
            throw new IllegalArgumentException("Analysis cache TTL must exceed jitter range");
        }
        this.keyPrefix = keyPrefix;
        this.baseTtl = baseTtl;
        this.clock = Objects.requireNonNull(clock);
    }

    /** TTL 연장 없는 건강 결과 조회 */
    @Override
    public Optional<CachedAnalysisResult<HealthAnalysisResult>> findHealth(AnalysisCacheKey key) {
        return read(key, AnalysisCacheFeature.HEALTH, HealthAnalysisResult.class);
    }

    /** 원 분석 열람자를 포함한 건강 결과 저장 */
    @Override
    public CachedAnalysisResult<HealthAnalysisResult> saveHealth(
            AnalysisCacheKey key,
            HealthAnalysisResult result,
            ArticleRevisionFingerprint articleFingerprint,
            String viewerFingerprint
    ) {
        return save(key, AnalysisCacheFeature.HEALTH, result, articleFingerprint, viewerFingerprint);
    }

    /** TTL 연장 없는 제목 결과 조회 */
    @Override
    public Optional<CachedAnalysisResult<HeadlineAnalysisResult>> findHeadline(AnalysisCacheKey key) {
        return read(key, AnalysisCacheFeature.HEADLINE, HeadlineAnalysisResult.class);
    }

    /** 원 분석 열람자를 포함한 제목 결과 저장 */
    @Override
    public CachedAnalysisResult<HeadlineAnalysisResult> saveHeadline(
            AnalysisCacheKey key,
            HeadlineAnalysisResult result,
            ArticleRevisionFingerprint articleFingerprint,
            String viewerFingerprint
    ) {
        return save(key, AnalysisCacheFeature.HEADLINE, result, articleFingerprint, viewerFingerprint);
    }

    /** 기능 일치 결과 Hash 역직렬화 */
    private <T> Optional<CachedAnalysisResult<T>> read(
            AnalysisCacheKey key,
            AnalysisCacheFeature expectedFeature,
            Class<T> resultType
    ) {
        Objects.requireNonNull(key);

        if (key.feature() != expectedFeature) {
            return Optional.empty();
        }

        Map<Object, Object> values = redisTemplate.opsForHash().entries(cacheKey(key));
        Object resultJson = values.get(RESULT_JSON);
        Object expiresAtValue = values.get(EXPIRES_AT);
        Object articleFingerprintJson = values.get(ARTICLE_FINGERPRINT_JSON);

        if (resultJson == null || expiresAtValue == null || articleFingerprintJson == null) {
            return Optional.empty();
        }

        try {
            Instant expiresAt = Instant.parse(expiresAtValue.toString());
            if (!clock.instant().isBefore(expiresAt)) {
                return Optional.empty();
            }
            T result = objectMapper.readValue(resultJson.toString(), resultType);
            ArticleRevisionFingerprint articleFingerprint = objectMapper.readValue(
                    articleFingerprintJson.toString(),
                    ArticleRevisionFingerprint.class
            );
            if (result == null) {
                return Optional.empty();
            }
            return Optional.of(new CachedAnalysisResult<>(
                    result,
                    expiresAt,
                    articleFingerprint
            ));
        } catch (DateTimeException | JacksonException exception) {
            return Optional.empty();
        }
    }

    /** 결과 Hash와 원 분석 열람 Marker의 동일 만료 저장 */
    private <T> CachedAnalysisResult<T> save(
            AnalysisCacheKey key,
            AnalysisCacheFeature expectedFeature,
            T result,
            ArticleRevisionFingerprint articleFingerprint,
            String viewerFingerprint
    ) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(result);
        Objects.requireNonNull(articleFingerprint);
        if (key.feature() != expectedFeature) {
            throw new IllegalArgumentException("Analysis cache feature does not match result type");
        }
        Instant expiresAt = clock.instant().plus(effectiveTtl(key));
        try {
            Long saved = redisTemplate.execute(
                    SAVE_SCRIPT,
                    List.of(cacheKey(key), viewerKey(key, viewerFingerprint)),
                    objectMapper.writeValueAsString(result),
                    expiresAt.toString(),
                    objectMapper.writeValueAsString(articleFingerprint),
                    Long.toString(expiresAt.toEpochMilli())
            );
            if (!Long.valueOf(1L).equals(saved)) {
                throw new IllegalStateException("Analysis cache result was not saved");
            }
            return new CachedAnalysisResult<>(result, expiresAt, articleFingerprint);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Analysis cache result cannot be written", exception);
        }
    }

    /** Key Digest 기반 0~30분 미만 결정적 감산 지터 */
    private Duration effectiveTtl(AnalysisCacheKey key) {
        long source = Long.parseUnsignedLong(key.id().substring(0, 8), 16);
        long jitterSeconds = source % (MAX_JITTER_SECONDS + 1);
        return baseTtl.minusSeconds(jitterSeconds);
    }

    /** 테스트와 이용량 Adapter가 공유하는 결과 Key */
    String cacheKey(AnalysisCacheKey key) {
        return RedisAnalysisCacheKeys.cacheKey(keyPrefix, key);
    }

    /** 테스트와 이용량 Adapter가 공유하는 열람 Marker Key */
    String viewerKey(AnalysisCacheKey key, String viewerFingerprint) {
        return RedisAnalysisCacheKeys.viewerKey(
                keyPrefix,
                key.feature(),
                key.id(),
                viewerFingerprint
        );
    }
}
