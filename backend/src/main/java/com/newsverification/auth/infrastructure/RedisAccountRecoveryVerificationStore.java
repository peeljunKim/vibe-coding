/* Redis 계정 복구 인증 상태 Adapter */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryException;
import com.newsverification.auth.application.AccountRecoveryVerificationStore;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 인증번호 Hash와 계정 복구 요청 제한 저장 */
public class RedisAccountRecoveryVerificationStore implements AccountRecoveryVerificationStore {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
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
                redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]))
            end
            return attempts
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String keyPrefix;
    private final ZoneId zoneId;
    private final Clock clock;

    public RedisAccountRecoveryVerificationStore(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            String keyPrefix,
            ZoneId zoneId,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.keyPrefix = Objects.requireNonNull(keyPrefix) + ":account-recovery:v1:";
        this.zoneId = Objects.requireNonNull(zoneId);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 새 계정 복구 인증번호 저장과 발송 제한 적용 */
    @Override
    public IssueResult issue(Purpose purpose, String lookupKey, String code) {
        Instant now = clock.instant();
        Long result = redisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(codeKey(purpose, lookupKey), dailyKey(purpose, lookupKey, now)),
                passwordEncoder.encode(code),
                Long.toString(now.toEpochMilli()),
                Long.toString(RESEND_INTERVAL.toMillis()),
                Integer.toString(MAX_DAILY_SENDS),
                Long.toString(CODE_TTL.toMillis()),
                Long.toString(nextMidnight(now).toEpochMilli())
        );
        if (result == null) {
            throw new IllegalStateException("Redis account recovery result is missing");
        }
        if (result == -1) {
            throw new AccountRecoveryException("VERIFICATION_BLOCKED");
        }
        if (result == -2) {
            throw new AccountRecoveryException("RESEND_TOO_SOON");
        }
        if (result == -3) {
            throw new AccountRecoveryException("DAILY_SEND_LIMIT_EXCEEDED");
        }
        return new IssueResult(MAX_ATTEMPTS, RESEND_INTERVAL.toSeconds());
    }

    /** 계정 복구 인증번호 일치와 실패 횟수 검사 */
    @Override
    public VerificationResult verify(Purpose purpose, String lookupKey, String code) {
        String key = codeKey(purpose, lookupKey);
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
    public void consume(Purpose purpose, String lookupKey) {
        redisTemplate.delete(codeKey(purpose, lookupKey));
    }

    private String codeKey(Purpose purpose, String lookupKey) {
        return keyPrefix + purpose.name().toLowerCase(Locale.ROOT) + ":code:" + lookupKey;
    }

    private String dailyKey(Purpose purpose, String lookupKey, Instant now) {
        return keyPrefix + purpose.name().toLowerCase(Locale.ROOT) + ":daily:"
                + lookupKey + ":" + LocalDate.ofInstant(now, zoneId);
    }

    private Instant nextMidnight(Instant now) {
        return LocalDate.ofInstant(now, zoneId).plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private long number(Object value) {
        return value == null ? 0 : Long.parseLong(value.toString());
    }
}
