/* Redis 분석 접수 요청 제한 Adapter */
package com.newsverification.analysis.infrastructure;

import com.newsverification.analysis.application.AnalysisRequestRateLimitExceededException;
import com.newsverification.analysis.application.AnalysisRequestRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 기능별 고정 시간과 비식별 Key 기반 원자 요청 제한 */
@Repository
public class RedisAnalysisRequestRateLimiter implements AnalysisRequestRateLimiter {

    private static final int DEFAULT_REQUEST_LIMIT = 5;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local maximumCount = 0
            for _, key in ipairs(KEYS) do
                local currentCount = tonumber(redis.call('GET', key) or '0')
                if currentCount > maximumCount then
                    maximumCount = currentCount
                end
            end
            local requestLimit = tonumber(ARGV[1])
            if maximumCount >= requestLimit then
                return 0
            end
            local windowMillis = tonumber(ARGV[2])
            local nextCount = maximumCount + 1
            for _, key in ipairs(KEYS) do
                local remainingTtl = redis.call('PTTL', key)
                redis.call('SET', key, nextCount)
                if remainingTtl > 0 then
                    redis.call('PEXPIRE', key, remainingTtl)
                else
                    redis.call('PEXPIRE', key, windowMillis)
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final int requestLimit;
    private final Duration window;

    /** 운영 Redis 연결과 1분 5회 정책 구성 */
    @Autowired
    public RedisAnalysisRequestRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix
    ) {
        this(redisTemplate, keyPrefix, DEFAULT_REQUEST_LIMIT, DEFAULT_WINDOW);
    }

    /** 테스트용 제한 횟수와 시간 구성 */
    RedisAnalysisRequestRateLimiter(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            int requestLimit,
            Duration window
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        if (requestLimit < 1) {
            throw new IllegalArgumentException("Analysis request limit must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Analysis request window must be positive");
        }
        this.keyPrefix = keyPrefix + ":analysis-request-rate:v1:";
        this.requestLimit = requestLimit;
        this.window = window;
    }

    /** 기능과 비식별 식별값의 원자 요청 접수 */
    @Override
    public void acquire(Feature feature, List<String> identifierKeys) {
        Objects.requireNonNull(feature);
        if (identifierKeys == null || identifierKeys.isEmpty()
                || identifierKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Analysis request identifier keys are required");
        }
        List<String> keys = identifierKeys.stream()
                .map(identifierKey -> keyFor(feature, identifierKey))
                .distinct()
                .toList();
        Long acquired = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                keys,
                Integer.toString(requestLimit),
                Long.toString(window.toMillis())
        );
        if (acquired == null) {
            throw new IllegalStateException("Redis analysis request rate limit returned no result");
        }
        if (acquired == 0L) {
            throw new AnalysisRequestRateLimitExceededException();
        }
    }

    /** 기능과 식별 Digest 기반 Redis Key 생성 */
    String keyFor(Feature feature, String identifierKey) {
        return keyPrefix
                + feature.name().toLowerCase(Locale.ROOT)
                + ":"
                + sha256(identifierKey);
    }

    /** 비식별 식별값의 추가 단방향 Digest */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
