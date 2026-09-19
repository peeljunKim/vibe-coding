/* Redis Streams 기사 제목 분석 Queue Adapter */
package com.newsverification.headline.infrastructure;

import com.newsverification.headline.application.HeadlineAnalysisQueue;
import com.newsverification.headline.application.HeadlineAnalysisTask;
import com.newsverification.headline.application.HeadlineAnalysisUserType;
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

/** 최대 20개 대기열과 제목 분석 단일 Worker Lease */
@Repository
public class RedisHeadlineAnalysisQueue implements HeadlineAnalysisQueue {

    private static final int MAX_WAITING_TASKS = 20;
    private static final String CONSUMER_GROUP = "headline-analysis-workers";
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

    /** Redis 연결과 제목 Queue Namespace 구성 */
    public RedisHeadlineAnalysisQueue(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        this.streamKey = keyPrefix + ":headline-analysis:queue:v1";
        this.workerLockKey = keyPrefix + ":headline-analysis:worker-lock:v1";
    }

    /** 대기 작업 상한 기반 원자적 Stream 추가 */
    @Override
    public boolean enqueue(HeadlineAnalysisTask task) {
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
    public Optional<HeadlineAnalysisTask> take() {
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

    /** 만료 시간이 있는 제목 Worker Lease 획득 */
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

    /** 소유권 비교 후 제목 Worker Lease 해제 */
    @Override
    public void releaseWorker(String ownerToken) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(workerLockKey), ownerToken);
    }

    /** Consumer Group 지연 생성 */
    private void ensureConsumerGroup() {
        redisTemplate.execute(CREATE_GROUP_SCRIPT, List.of(streamKey), CONSUMER_GROUP);
    }

    /** Stream 필드의 제목 작업 복원 */
    private HeadlineAnalysisTask toTask(Map<Object, Object> values) {
        return new HeadlineAnalysisTask(
                requiredValue(values, ANALYSIS_ID),
                requiredValue(values, ARTICLE_URL),
                HeadlineAnalysisUserType.valueOf(requiredValue(values, USER_TYPE)),
                Arrays.stream(requiredValue(values, USAGE_KEYS).split(","))
                        .filter(value -> !value.isBlank())
                        .toList()
        );
    }

    /** 필수 Stream 필드 조회 */
    private String requiredValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("Headline queue field is missing: " + field);
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
