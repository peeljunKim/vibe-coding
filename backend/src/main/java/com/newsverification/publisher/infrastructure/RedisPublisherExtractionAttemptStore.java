/* Redis 언론사 추출 실패율 Sliding Window Adapter */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.application.PublisherExtractionAttemptStore;
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
import java.util.Objects;
import java.util.UUID;

/** 최근 1시간 10회·실패율 70% 원자 판정 */
@Repository
public class RedisPublisherExtractionAttemptStore implements PublisherExtractionAttemptStore {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);
    private static final int DEFAULT_MINIMUM_ATTEMPTS = 10;
    private static final int DEFAULT_FAILURE_PERCENT = 70;
    private static final RedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>("""
            local redisTime = redis.call('TIME')
            local nowMillis = redisTime[1] * 1000 + math.floor(redisTime[2] / 1000)
            local cutoff = nowMillis - tonumber(ARGV[1])
            local member = ARGV[2]
            redis.call('ZADD', KEYS[1], nowMillis, member)
            if ARGV[3] == '1' then
                redis.call('ZADD', KEYS[2], nowMillis, member)
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', cutoff)
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('PEXPIRE', KEYS[2], ARGV[1])
            end
            local total = redis.call('ZCARD', KEYS[1])
            local failures = redis.call('ZCARD', KEYS[2])
            if total >= tonumber(ARGV[4])
                    and failures * 100 >= total * tonumber(ARGV[5]) then
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration window;
    private final int minimumAttempts;
    private final int failurePercent;

    /** 운영 Redis와 확정 자동 중단 정책 구성 */
    @Autowired
    public RedisPublisherExtractionAttemptStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix
    ) {
        this(
                redisTemplate,
                keyPrefix,
                DEFAULT_WINDOW,
                DEFAULT_MINIMUM_ATTEMPTS,
                DEFAULT_FAILURE_PERCENT
        );
    }

    /** 테스트용 시간 창과 임계치 구성 */
    RedisPublisherExtractionAttemptStore(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            Duration window,
            int minimumAttempts,
            int failurePercent
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Publisher extraction window must be positive");
        }
        if (minimumAttempts < 1 || failurePercent < 1 || failurePercent > 100) {
            throw new IllegalArgumentException("Publisher extraction threshold is invalid");
        }
        this.keyPrefix = keyPrefix + ":publisher-extraction-health:v1:";
        this.window = window;
        this.minimumAttempts = minimumAttempts;
        this.failurePercent = failurePercent;
    }

    /** 언론사별 성공·실패 추가와 자동 중단 판정 */
    @Override
    public boolean record(long publisherId, boolean failed) {
        if (publisherId < 1) {
            throw new IllegalArgumentException("Publisher ID must be positive");
        }
        String baseKey = keyPrefix + sha256(Long.toString(publisherId));
        Long shouldPause = redisTemplate.execute(
                RECORD_SCRIPT,
                List.of(baseKey + ":attempts", baseKey + ":failures"),
                Long.toString(window.toMillis()),
                UUID.randomUUID().toString(),
                failed ? "1" : "0",
                Integer.toString(minimumAttempts),
                Integer.toString(failurePercent)
        );
        if (shouldPause == null) {
            throw new IllegalStateException("Redis publisher extraction policy returned no result");
        }
        return shouldPause == 1L;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
