/* Redis 기사 제목 분석 이용량 Adapter */
package com.newsverification.headline.infrastructure;

import com.newsverification.headline.application.HeadlineAnalysisUsagePolicy;
import com.newsverification.headline.application.HeadlineAnalysisUsageResult;
import com.newsverification.headline.application.HeadlineAnalysisUsageSubject;
import com.newsverification.headline.application.HeadlineDailyUsageLimitExceededException;
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

/** 한국시간 일일 Key와 원자적 제목 이용량 차감 */
@Repository
public class RedisHeadlineAnalysisUsagePolicy implements HeadlineAnalysisUsagePolicy {

    private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local usedCount = 0
            local existingKeyCount = 0
            for _, key in ipairs(KEYS) do
                if redis.call('EXISTS', key) == 1 then existingKeyCount = existingKeyCount + 1 end
                local current = tonumber(redis.call('GET', key) or '0')
                if current > usedCount then usedCount = current end
            end
            if existingKeyCount > 0 then
                for _, key in ipairs(KEYS) do
                    redis.call('SET', key, usedCount)
                    redis.call('PEXPIREAT', key, ARGV[1])
                end
            end
            return usedCount
            """, Long.class);
    private static final RedisScript<Long> RECORD_SCRIPT = new DefaultRedisScript<>("""
            local usedCount = 0
            for _, key in ipairs(KEYS) do
                local current = tonumber(redis.call('GET', key) or '0')
                if current > usedCount then usedCount = current end
            end
            local dailyLimit = tonumber(ARGV[1])
            if usedCount >= dailyLimit then return -(usedCount + 1) end
            usedCount = usedCount + 1
            for _, key in ipairs(KEYS) do
                redis.call('SET', key, usedCount)
                redis.call('PEXPIREAT', key, ARGV[2])
            end
            return usedCount
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final ZoneId resetZone;
    private final Clock clock;

    /** Redis 연결과 한국시간 일일 Namespace 구성 */
    @Autowired
    public RedisHeadlineAnalysisUsagePolicy(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix,
            @Value("${app.time-zone:Asia/Seoul}") String resetZone
    ) {
        this(redisTemplate, keyPrefix, ZoneId.of(resetZone), Clock.systemUTC());
    }

    /** 테스트 제어용 시계 포함 구성 */
    RedisHeadlineAnalysisUsagePolicy(
            StringRedisTemplate redisTemplate,
            String keyPrefix,
            ZoneId resetZone,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        this.keyPrefix = keyPrefix + ":headline-usage:v1:";
        this.resetZone = Objects.requireNonNull(resetZone);
        this.clock = Objects.requireNonNull(clock);
    }

    /** TTL 변경 없는 제목 분석 한도 확인 */
    @Override
    public HeadlineAnalysisUsageResult currentUsage(HeadlineAnalysisUsageSubject subject) {
        Long result = redisTemplate.execute(
                VERIFY_SCRIPT,
                keysFor(subject),
                Long.toString(nextResetAt().toEpochMilli())
        );
        if (result == null) {
            throw new IllegalStateException("Redis headline usage result is missing");
        }
        return new HeadlineAnalysisUsageResult(
                Math.toIntExact(result),
                subject.userType().dailyLimit()
        );
    }

    /** TTL 변경 없는 제목 분석 한도 확인 */
    @Override
    public void verifyCanStart(HeadlineAnalysisUsageSubject subject) {
        HeadlineAnalysisUsageResult usage = currentUsage(subject);
        rejectExceeded(subject, usage.usedCount());
    }

    /** 실제 분석 시작 시 일일 횟수 원자 증가 */
    @Override
    public HeadlineAnalysisUsageResult recordAnalysisStart(HeadlineAnalysisUsageSubject subject) {
        int dailyLimit = subject.userType().dailyLimit();
        Long result = redisTemplate.execute(
                RECORD_SCRIPT,
                keysFor(subject),
                Integer.toString(dailyLimit),
                Long.toString(nextResetAt().toEpochMilli())
        );
        if (result == null) {
            throw new IllegalStateException("Redis headline usage result is missing");
        }
        if (result < 0) {
            throw new HeadlineDailyUsageLimitExceededException(Math.toIntExact(-result - 1), dailyLimit);
        }
        return new HeadlineAnalysisUsageResult(Math.toIntExact(result), dailyLimit);
    }

    /** 복수 식별 신호의 Redis Key 변환 */
    List<String> keysFor(HeadlineAnalysisUsageSubject subject) {
        Objects.requireNonNull(subject);
        LocalDate usageDate = clock.instant().atZone(resetZone).toLocalDate();
        String prefix = keyPrefix + subject.userType().name().toLowerCase(Locale.ROOT)
                + ":" + usageDate + ":";
        return subject.identifierKeys().stream()
                .map(key -> prefix + hash(key))
                .distinct()
                .toList();
    }

    /** 다음 한국시간 자정의 절대 만료 시각 */
    private Instant nextResetAt() {
        return clock.instant().atZone(resetZone).toLocalDate().plusDays(1)
                .atStartOfDay(resetZone).toInstant();
    }

    /** 한도 도달 상태 예외 변환 */
    private void rejectExceeded(HeadlineAnalysisUsageSubject subject, int usedCount) {
        if (usedCount >= subject.userType().dailyLimit()) {
            throw new HeadlineDailyUsageLimitExceededException(
                    usedCount, subject.userType().dailyLimit()
            );
        }
    }

    /** Redis Key용 식별값 단방향 해시 */
    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
