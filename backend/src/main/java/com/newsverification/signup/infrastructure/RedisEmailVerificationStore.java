/* Redis 이메일 인증 상태 Adapter */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.EmailVerificationStore;
import com.newsverification.signup.application.SignupException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 인증번호 Hash와 발송·실패 제한 저장 */
public class RedisEmailVerificationStore implements EmailVerificationStore {

    private static final Duration CODE_TTL = Duration.ofHours(24);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);
    private static final Duration FAILURE_BLOCK = Duration.ofMinutes(30);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_DAILY_SENDS = 5;

    private static final RedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            local blockedUntil = tonumber(redis.call('HGET', KEYS[1], 'blockedUntil') or '0')
            if blockedUntil > tonumber(ARGV[2]) then return -1 end
            local lastSentAt = tonumber(redis.call('HGET', KEYS[1], 'lastSentAt') or '0')
            if lastSentAt > 0 and lastSentAt + tonumber(ARGV[3]) > tonumber(ARGV[2]) then return -2 end
            local sentCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if sentCount >= tonumber(ARGV[4]) then return -3 end
            redis.call('HSET', KEYS[1],
                'codeHash', ARGV[1],
                'attempts', '0',
                'lastSentAt', ARGV[2],
                'blockedUntil', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            sentCount = redis.call('INCR', KEYS[2])
            if sentCount == 1 then redis.call('PEXPIREAT', KEYS[2], ARGV[6]) end
            return sentCount
            """, Long.class);

    private static final RedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local blockedUntil = tonumber(redis.call('HGET', KEYS[1], 'blockedUntil') or '0')
            if blockedUntil > tonumber(ARGV[1]) then return -2 end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[2]) then
                redis.call('HSET', KEYS[1], 'blockedUntil', tonumber(ARGV[1]) + tonumber(ARGV[3]))
            end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String keyPrefix;
    private final ZoneId zoneId;
    private final Clock clock;

    public RedisEmailVerificationStore(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            String keyPrefix,
            ZoneId zoneId,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.keyPrefix = Objects.requireNonNull(keyPrefix) + ":signup-email:v1:";
        this.zoneId = Objects.requireNonNull(zoneId);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 새 인증번호 저장과 재발송 제한 적용 */
    @Override
    public IssueResult issue(long userId, String code) {
        Instant now = clock.instant();
        Long result = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(codeKey(userId), dailyKey(userId, now)),
                passwordEncoder.encode(code),
                Long.toString(now.toEpochMilli()),
                Long.toString(RESEND_INTERVAL.toMillis()),
                Integer.toString(MAX_DAILY_SENDS),
                Long.toString(CODE_TTL.toMillis()),
                Long.toString(nextMidnight(now).toEpochMilli())
        );
        if (result == null) {
            throw new IllegalStateException("Redis signup verification result is missing");
        }
        if (result == -1) {
            throw new SignupException("VERIFICATION_BLOCKED");
        }
        if (result == -2) {
            throw new SignupException("RESEND_TOO_SOON");
        }
        if (result == -3) {
            throw new SignupException("DAILY_SEND_LIMIT_EXCEEDED");
        }
        return new IssueResult(MAX_ATTEMPTS, RESEND_INTERVAL.toSeconds());
    }

    /** 인증번호 일치와 실패 횟수 검사 */
    @Override
    public VerificationResult verify(long userId, String code) {
        String key = codeKey(userId);
        Map<Object, Object> state = redisTemplate.opsForHash().entries(key);
        if (state.isEmpty()) {
            return VerificationResult.EXPIRED;
        }
        long now = clock.instant().toEpochMilli();
        if (number(state.get("blockedUntil")) > now || number(state.get("attempts")) >= MAX_ATTEMPTS) {
            return VerificationResult.BLOCKED;
        }
        Object storedHash = state.get("codeHash");
        if (storedHash != null && passwordEncoder.matches(code, storedHash.toString())) {
            return VerificationResult.VERIFIED;
        }

        Long attempts = redisTemplate.execute(
                FAILURE_SCRIPT,
                List.of(key),
                Long.toString(now),
                Integer.toString(MAX_ATTEMPTS),
                Long.toString(FAILURE_BLOCK.toMillis())
        );
        if (attempts == null || attempts == -1) {
            return VerificationResult.EXPIRED;
        }
        return attempts >= MAX_ATTEMPTS || attempts == -2
                ? VerificationResult.BLOCKED
                : VerificationResult.INVALID;
    }

    @Override
    public void consume(long userId) {
        redisTemplate.delete(codeKey(userId));
    }

    private String codeKey(long userId) {
        return keyPrefix + "code:" + userId;
    }

    private String dailyKey(long userId, Instant now) {
        return keyPrefix + "daily:" + userId + ":" + LocalDate.ofInstant(now, zoneId);
    }

    private Instant nextMidnight(Instant now) {
        return LocalDate.ofInstant(now, zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private long number(Object value) {
        return value == null ? 0 : Long.parseLong(value.toString());
    }
}
