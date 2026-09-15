/* Redis 건강 분야 판별 실패 이용량 Adapter */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisUsageSubject;
import com.newsverification.health.application.HealthDailyUsageLimitExceededException;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import com.newsverification.health.application.HealthTopicFailureUsageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 한국시간 일일 Key와 Lua 원자 처리 기반 이용량 정책 */
@Repository
public class RedisHealthTopicFailureUsagePolicy implements HealthTopicFailureUsagePolicy {

    private static final RedisScript<Long> VERIFY_USAGE_SCRIPT = new DefaultRedisScript<>("""
            local usedCount = 0
            local failureCount = 0
            local existingKeyCount = 0
            for _, key in ipairs(KEYS) do
                if redis.call('EXISTS', key) == 1 then
                    existingKeyCount = existingKeyCount + 1
                end
                local current = tonumber(redis.call('HGET', key, 'usedCount') or '0')
                local currentFailure = tonumber(redis.call('HGET', key, 'topicFailureCount') or '0')
                if current > usedCount then
                    usedCount = current
                end
                if currentFailure > failureCount then
                    failureCount = currentFailure
                end
            end
            if existingKeyCount > 0 then
                for _, key in ipairs(KEYS) do
                    redis.call('HSET', key,
                        'usedCount', usedCount,
                        'topicFailureCount', failureCount)
                    redis.call('PEXPIREAT', key, ARGV[1])
                end
            end
            return usedCount
            """, Long.class);

    private static final RedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local usedCount = 0
            local failureCount = 0
            for _, key in ipairs(KEYS) do
                local currentUsed = tonumber(redis.call('HGET', key, 'usedCount') or '0')
                local currentFailure = tonumber(redis.call('HGET', key, 'topicFailureCount') or '0')
                if currentUsed > usedCount then
                    usedCount = currentUsed
                end
                if currentFailure > failureCount then
                    failureCount = currentFailure
                end
            end
            local dailyLimit = tonumber(ARGV[1])
            if usedCount >= dailyLimit then
                return -(usedCount + 1)
            end
            local charged = 0
            if failureCount >= 1 then
                usedCount = usedCount + 1
                charged = 1
            end
            failureCount = failureCount + 1
            for _, key in ipairs(KEYS) do
                redis.call('HSET', key,
                    'usedCount', usedCount,
                    'topicFailureCount', failureCount)
                redis.call('PEXPIREAT', key, ARGV[2])
            end
            return usedCount * 2 + charged
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final ZoneId resetZone;
    private final Clock clock;

    /** Redis 연결과 한국시간 일일 Namespace 구성 */
    @Autowired
    public RedisHealthTopicFailureUsagePolicy(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix,
            @Value("${app.time-zone:Asia/Seoul}") String resetZone
    ) {
        this(redisTemplate, keyPrefix, ZoneId.of(resetZone), Clock.systemUTC());
    }

    /** 테스트 제어용 시계 포함 구성 */
    RedisHealthTopicFailureUsagePolicy(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            ZoneId resetZone,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        this.keyPrefix = keyPrefix + ":health-usage:v1:";
        this.resetZone = Objects.requireNonNull(resetZone);
        this.clock = Objects.requireNonNull(clock);
    }

    /** TTL 변경 없는 신규 건강 분석 한도 확인 */
    @Override
    public void verifyCanStart(HealthAnalysisUsageSubject subject) {
        Objects.requireNonNull(subject);
        Long storedCount = redisTemplate.execute(
                VERIFY_USAGE_SCRIPT,
                keysFor(subject),
                Long.toString(nextResetAt().toEpochMilli())
        );
        if (storedCount == null) {
            throw new IllegalStateException("Redis health usage result is missing");
        }
        int usedCount = Math.toIntExact(storedCount);
        rejectExceeded(subject, usedCount);
    }

    /** 첫 실패 무료와 이후 차감 및 한도 검사의 원자 처리 */
    @Override
    public HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject) {
        Objects.requireNonNull(subject);
        int dailyLimit = subject.userType().dailyLimit();
        Long result = redisTemplate.execute(
                RECORD_FAILURE_SCRIPT,
                keysFor(subject),
                Integer.toString(dailyLimit),
                Long.toString(nextResetAt().toEpochMilli())
        );
        if (result == null) {
            throw new IllegalStateException("Redis health usage result is missing");
        }
        if (result < 0) {
            throw new HealthDailyUsageLimitExceededException(Math.toIntExact(-result - 1), dailyLimit);
        }
        boolean charged = result % 2 == 1;
        int usedCount = Math.toIntExact(result / 2);
        return new HealthTopicFailureUsageResult(charged, usedCount, dailyLimit);
    }

    /** 이용자 유형과 일자 및 비식별 식별값 기반 Key */
    String keyFor(HealthAnalysisUsageSubject subject) {
        return keysFor(subject).get(0);
    }

    /** 복수 식별 신호의 Redis Key 변환 */
    List<String> keysFor(HealthAnalysisUsageSubject subject) {
        Objects.requireNonNull(subject);
        LocalDate usageDate = clock.instant().atZone(resetZone).toLocalDate();
        String subjectPrefix = keyPrefix
                + subject.userType().name().toLowerCase(Locale.ROOT)
                + ":" + usageDate + ":";
        return subject.identifierKeys().stream()
                .map(identifierKey -> subjectPrefix + hash(identifierKey))
                .distinct()
                .toList();
    }

    /** 다음 한국시간 자정의 절대 만료 시각 */
    private Instant nextResetAt() {
        return clock.instant()
                .atZone(resetZone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(resetZone)
                .toInstant();
    }

    /** 한도 도달 상태 예외 변환 */
    private void rejectExceeded(HealthAnalysisUsageSubject subject, int usedCount) {
        int dailyLimit = subject.userType().dailyLimit();
        if (usedCount >= dailyLimit) {
            throw new HealthDailyUsageLimitExceededException(usedCount, dailyLimit);
        }
    }

    /** Redis Key용 식별값 단방향 해시 */
    private String hash(String identifierKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(identifierKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
