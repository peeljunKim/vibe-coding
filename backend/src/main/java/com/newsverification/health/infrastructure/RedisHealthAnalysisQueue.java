/* Redis Streams 건강 분석 Queue Adapter */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisQueue;
import com.newsverification.health.application.HealthAnalysisTask;
import com.newsverification.health.application.HealthAnalysisUserType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 최대 20개 대기열과 전역 단일 Worker Lease */
@Repository
public class RedisHealthAnalysisQueue implements HealthAnalysisQueue {

    private static final int MAX_WAITING_TASKS = 20;
    private static final String CONSUMER_GROUP = "health-analysis-workers";
    private static final String CONSUMER_NAME = "single-worker";
    private static final String ANALYSIS_ID = "analysisId";
    private static final String ARTICLE_URL = "articleUrl";
    private static final String USER_TYPE = "userType";
    private static final String USAGE_KEYS = "usageKeys";

    private static final RedisScript<Long> ENQUEUE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('XLEN', KEYS[1]) >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('XADD', KEYS[1], '*',
                'analysisId', ARGV[2],
                'articleUrl', ARGV[3],
                'userType', ARGV[4],
                'usageKeys', ARGV[5])
            return 1
            """, Long.class);
    private static final RedisScript<Long> CREATE_GROUP_SCRIPT = new DefaultRedisScript<>("""
            local result = redis.pcall('XGROUP', 'CREATE', KEYS[1], ARGV[1], '0', 'MKSTREAM')
            if type(result) == 'table' and result.err and not string.find(result.err, 'BUSYGROUP') then
                return redis.error_reply(result.err)
            end
            return 1
            """, Long.class);
    private static final RedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String workerLockKey;

    /** Redis 연결과 Queue Namespace 구성 */
    public RedisHealthAnalysisQueue(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        this.streamKey = keyPrefix + ":health-analysis:queue:v1";
        this.workerLockKey = keyPrefix + ":health-analysis:worker-lock:v1";
    }

    /** 대기 작업 상한을 확인하는 원자적 Stream 추가 */
    @Override
    public boolean enqueue(HealthAnalysisTask task) {
        Objects.requireNonNull(task);
        Long result = redisTemplate.execute(
                ENQUEUE_SCRIPT,
                List.of(streamKey),
                Integer.toString(MAX_WAITING_TASKS),
                task.analysisId(),
                task.articleUrl(),
                task.userType().name(),
                String.join(",", task.usageIdentifierKeys())
        );
        return Long.valueOf(1).equals(result);
    }

    /** Consumer Group 수신 직후 승인·삭제하는 무재시도 소비 */
    @Override
    public Optional<HealthAnalysisTask> take() {
        ensureConsumerGroup();
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamReadOptions.empty().count(1),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );
        if (records == null || records.isEmpty()) {
            return Optional.empty();
        }

        MapRecord<String, Object, Object> record = records.get(0);
        redisTemplate.opsForStream().acknowledge(streamKey, CONSUMER_GROUP, record.getId());
        redisTemplate.opsForStream().delete(streamKey, record.getId());
        return Optional.of(toTask(record.getValue()));
    }

    /** 만료 시간이 있는 전역 Worker Lease 획득 */
    @Override
    public boolean tryAcquireWorker(String ownerToken, Duration leaseTime) {
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("Worker owner token is required");
        }
        if (leaseTime == null || leaseTime.isNegative() || leaseTime.isZero()) {
            throw new IllegalArgumentException("Worker lease time must be positive");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(workerLockKey, ownerToken, leaseTime));
    }

    /** 소유권 비교 후 Worker Lease 해제 */
    @Override
    public void releaseWorker(String ownerToken) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(workerLockKey), ownerToken);
    }

    /** Consumer Group 지연 생성 */
    private void ensureConsumerGroup() {
        redisTemplate.execute(CREATE_GROUP_SCRIPT, List.of(streamKey), CONSUMER_GROUP);
    }

    /** Stream 필드의 Queue 작업 복원 */
    private HealthAnalysisTask toTask(Map<Object, Object> values) {
        String usageKeys = requiredValue(values, USAGE_KEYS);
        return new HealthAnalysisTask(
                requiredValue(values, ANALYSIS_ID),
                requiredValue(values, ARTICLE_URL),
                HealthAnalysisUserType.valueOf(requiredValue(values, USER_TYPE)),
                Arrays.stream(usageKeys.split(","))
                        .filter(value -> !value.isBlank())
                        .toList()
        );
    }

    /** 필수 Stream 필드 조회 */
    private String requiredValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("Health analysis queue field is missing: " + field);
        }
        return value.toString();
    }

    /** 테스트 Namespace Stream Key */
    String streamKey() {
        return streamKey;
    }

    /** 테스트 Namespace Worker Lock Key */
    String workerLockKey() {
        return workerLockKey;
    }
}
