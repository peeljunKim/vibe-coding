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

    private static final RedisScript<String> RESERVE_ATTEMPT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 'EXPIRED|' end
            local blockedUntil = tonumber(redis.call('HGET', KEYS[1], 'blockedUntil') or '0')
            if blockedUntil > tonumber(ARGV[1]) then return 'BLOCKED|' end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if attempts >= tonumber(ARGV[2]) then return 'BLOCKED|' end
            local codeHash = redis.call('HGET', KEYS[1], 'codeHash') or ''
            attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[2]) then
                redis.call('HSET', KEYS[1], 'blockedUntil', tonumber(ARGV[1]) + tonumber(ARGV[3]))
                redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]))
                return 'LAST|' .. codeHash
            end
            return 'OK|' .. codeHash
            """, String.class);

    private static final RedisScript<Long> DELETE_IF_HASH_MATCHES_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'codeHash') == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
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
        long now = clock.instant().toEpochMilli();
        String reservation = redisTemplate.execute(
                RESERVE_ATTEMPT_SCRIPT,
                List.of(key),
                Long.toString(now),
                Integer.toString(MAX_ATTEMPTS),
                Long.toString(FAILURE_BLOCK.toMillis())
        );
        if (reservation == null || reservation.startsWith("EXPIRED|")) {
            return VerificationResult.EXPIRED;
        }
        if (reservation.startsWith("BLOCKED|")) {
            return VerificationResult.BLOCKED;
        }
        int separator = reservation.indexOf('|');
        if (separator < 0 || separator == reservation.length() - 1) {
            throw new IllegalStateException("Redis account recovery reservation is invalid");
        }
        String status = reservation.substring(0, separator);
        String storedHash = reservation.substring(separator + 1);
        if (!"OK".equals(status) && !"LAST".equals(status)) {
            throw new IllegalStateException("Redis account recovery reservation status is invalid");
        }
        if (passwordEncoder.matches(code, storedHash)) {
            return VerificationResult.VERIFIED;
        }
        return "LAST".equals(status) ? VerificationResult.BLOCKED : VerificationResult.INVALID;
    }

    @Override
    public void consume(Purpose purpose, String lookupKey) {
        redisTemplate.delete(codeKey(purpose, lookupKey));
    }

    /** 발송 실패 인증번호와 현재 상태 일치 시 보상 삭제 */
    @Override
    public void consumeIfCodeMatches(Purpose purpose, String lookupKey, String code) {
        String key = codeKey(purpose, lookupKey);
        Object currentHash = redisTemplate.opsForHash().get(key, "codeHash");
        if (currentHash == null || !passwordEncoder.matches(code, currentHash.toString())) {
            return;
        }
        redisTemplate.execute(
                DELETE_IF_HASH_MATCHES_SCRIPT,
                List.of(key),
                currentHash.toString()
        );
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

}
