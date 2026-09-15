/* Redis 비동기 분석 작업 저장 Adapter */
package com.newsverification.analysis.infrastructure;

import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Redis Hash와 원자적 Version 비교 기반 작업 저장소 */
@Repository
public class RedisAnalysisJobStore implements AnalysisJobStore {

    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final String STAGE = "stage";
    private static final String ACCEPTED_AT = "acceptedAt";
    private static final String DEADLINE_AT = "deadlineAt";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String VERSION = "version";

    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'id', ARGV[1],
                'status', ARGV[2],
                'stage', ARGV[3],
                'acceptedAt', ARGV[4],
                'deadlineAt', ARGV[5],
                'expiresAt', ARGV[6],
                'version', ARGV[7])
            redis.call('PEXPIREAT', KEYS[1], ARGV[8])
            return 1
            """, Long.class);

    private static final RedisScript<Long> REPLACE_SCRIPT = new DefaultRedisScript<>("""
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion or currentVersion ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'id', ARGV[2],
                'status', ARGV[3],
                'stage', ARGV[4],
                'acceptedAt', ARGV[5],
                'deadlineAt', ARGV[6],
                'expiresAt', ARGV[7],
                'version', ARGV[8])
            redis.call('PEXPIREAT', KEYS[1], ARGV[9])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    /** Redis 연결과 환경별 Key Namespace 구성 */
    public RedisAnalysisJobStore(
            StringRedisTemplate redisTemplate,
            @Value("${app.redis.key-prefix}") String keyPrefix
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis key prefix is required");
        }
        this.keyPrefix = keyPrefix + ":analysis-job:v1:";
    }

    /** 중복 없는 작업 생성과 절대 만료 설정 */
    @Override
    public boolean create(AnalysisJob job) {
        Objects.requireNonNull(job);
        Long result = redisTemplate.execute(
                CREATE_SCRIPT,
                List.of(keyFor(job.id())),
                jobArguments(job)
        );
        return Long.valueOf(1).equals(result);
    }

    /** 수명 연장 없는 작업 조회 */
    @Override
    public Optional<AnalysisJob> findById(String jobId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(keyFor(jobId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toJob(values));
    }

    /** Version 일치 시 상태와 만료의 원자적 교체 */
    @Override
    public boolean replace(String jobId, long expectedVersion, AnalysisJob updatedJob) {
        Objects.requireNonNull(updatedJob);
        if (!jobId.equals(updatedJob.id())) {
            throw new IllegalArgumentException("Analysis job id cannot change");
        }
        Long result = redisTemplate.execute(
                REPLACE_SCRIPT,
                List.of(keyFor(jobId)),
                replaceArguments(expectedVersion, updatedJob)
        );
        return Long.valueOf(1).equals(result);
    }

    /** 실행별 Namespace가 포함된 작업 Key */
    String keyFor(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Analysis job id is required");
        }
        return keyPrefix + jobId;
    }

    /** Redis Hash 저장 인수 변환 */
    private String[] jobArguments(AnalysisJob job) {
        return new String[] {
                job.id(),
                job.status().name(),
                job.stage().name(),
                job.acceptedAt().toString(),
                job.deadlineAt().toString(),
                job.expiresAt().toString(),
                Long.toString(job.version()),
                Long.toString(job.expiresAt().toEpochMilli())
        };
    }

    /** 조건부 교체 인수 변환 */
    private String[] replaceArguments(long expectedVersion, AnalysisJob job) {
        String[] jobArguments = jobArguments(job);
        String[] arguments = new String[jobArguments.length + 1];
        arguments[0] = Long.toString(expectedVersion);
        System.arraycopy(jobArguments, 0, arguments, 1, jobArguments.length);
        return arguments;
    }

    /** Redis Hash의 Domain 상태 복원 */
    private AnalysisJob toJob(Map<Object, Object> values) {
        return new AnalysisJob(
                requiredValue(values, ID),
                AnalysisJobStatus.valueOf(requiredValue(values, STATUS)),
                AnalysisJobStage.valueOf(requiredValue(values, STAGE)),
                instant(values, ACCEPTED_AT),
                instant(values, DEADLINE_AT),
                instant(values, EXPIRES_AT),
                Long.parseLong(requiredValue(values, VERSION))
        );
    }

    /** 필수 Hash 값 조회 */
    private String requiredValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null) {
            throw new IllegalStateException("Analysis job field is missing: " + field);
        }
        return value.toString();
    }

    /** ISO-8601 시각 복원 */
    private Instant instant(Map<Object, Object> values, String field) {
        return Instant.parse(requiredValue(values, field));
    }
}
