/* Redis 비동기 분석 작업 저장 Adapter */
package com.newsverification.analysis.infrastructure;

import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;
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
public class RedisAnalysisJobStore implements AnalysisJobStore, AnalysisJobOutcomeStore {

    private static final String ID = "id";
    private static final String OWNER_TYPE = "ownerType";
    private static final String OWNER_ACCESS_KEY_HASH = "ownerAccessKeyHash";
    private static final String STATUS = "status";
    private static final String STAGE = "stage";
    private static final String ACCEPTED_AT = "acceptedAt";
    private static final String DEADLINE_AT = "deadlineAt";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String VERSION = "version";
    private static final String OUTCOME_TYPE = "outcomeType";
    private static final String RESULT_JSON = "resultJson";
    private static final String ERROR_CODE = "errorCode";
    private static final String ERROR_DETAIL = "errorDetail";
    private static final String USAGE_JSON = "usageJson";

    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'id', ARGV[1],
                'ownerType', ARGV[2],
                'ownerAccessKeyHash', ARGV[3],
                'status', ARGV[4],
                'stage', ARGV[5],
                'acceptedAt', ARGV[6],
                'deadlineAt', ARGV[7],
                'expiresAt', ARGV[8],
                'version', ARGV[9])
            redis.call('PEXPIREAT', KEYS[1], ARGV[10])
            return 1
            """, Long.class);

    private static final RedisScript<Long> REPLACE_SCRIPT = new DefaultRedisScript<>("""
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion or currentVersion ~= ARGV[1] then
                return 0
            end
            local currentOwnerType = redis.call('HGET', KEYS[1], 'ownerType')
            local currentOwnerAccessKeyHash = redis.call('HGET', KEYS[1], 'ownerAccessKeyHash')
            if currentOwnerType ~= ARGV[3] or currentOwnerAccessKeyHash ~= ARGV[4] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'id', ARGV[2],
                'ownerType', ARGV[3],
                'ownerAccessKeyHash', ARGV[4],
                'status', ARGV[5],
                'stage', ARGV[6],
                'acceptedAt', ARGV[7],
                'deadlineAt', ARGV[8],
                'expiresAt', ARGV[9],
                'version', ARGV[10])
            redis.call('PEXPIREAT', KEYS[1], ARGV[11])
            return 1
            """, Long.class);
    private static final RedisScript<Long> REPLACE_WITH_OUTCOME_SCRIPT = new DefaultRedisScript<>("""
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion or currentVersion ~= ARGV[1] then
                return 0
            end
            local currentOwnerType = redis.call('HGET', KEYS[1], 'ownerType')
            local currentOwnerAccessKeyHash = redis.call('HGET', KEYS[1], 'ownerAccessKeyHash')
            if currentOwnerType ~= ARGV[3] or currentOwnerAccessKeyHash ~= ARGV[4] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'id', ARGV[2],
                'ownerType', ARGV[3],
                'ownerAccessKeyHash', ARGV[4],
                'status', ARGV[5],
                'stage', ARGV[6],
                'acceptedAt', ARGV[7],
                'deadlineAt', ARGV[8],
                'expiresAt', ARGV[9],
                'version', ARGV[10],
                'outcomeType', ARGV[12],
                'resultJson', ARGV[13],
                'errorCode', ARGV[14],
                'errorDetail', ARGV[15],
                'usageJson', ARGV[16])
            redis.call('PEXPIREAT', KEYS[1], ARGV[11])
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
        this.keyPrefix = keyPrefix + ":analysis-job:v2:";
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

    /** Version 일치 시 종료 상태와 결과의 원자적 교체 */
    @Override
    public boolean replaceWithOutcome(
            String jobId,
            long expectedVersion,
            AnalysisJob updatedJob,
            AnalysisJobOutcome outcome
    ) {
        Objects.requireNonNull(updatedJob);
        Objects.requireNonNull(outcome);
        if (!jobId.equals(updatedJob.id())) {
            throw new IllegalArgumentException("Analysis job id cannot change");
        }
        if ((updatedJob.status() == AnalysisJobStatus.COMPLETED
                && outcome.type() != AnalysisJobOutcome.Type.RESULT)
                || (updatedJob.status() == AnalysisJobStatus.FAILED
                && outcome.type() != AnalysisJobOutcome.Type.FAILURE)
                || updatedJob.status() == AnalysisJobStatus.PROCESSING) {
            throw new IllegalArgumentException("Analysis job outcome does not match terminal status");
        }
        Long result = redisTemplate.execute(
                REPLACE_WITH_OUTCOME_SCRIPT,
                List.of(keyFor(jobId)),
                outcomeArguments(expectedVersion, updatedJob, outcome)
        );
        return Long.valueOf(1).equals(result);
    }

    /** 수명 연장 없는 종료 결과 조회 */
    @Override
    public Optional<AnalysisJobOutcome> findOutcome(String jobId) {
        Map<Object, Object> values = redisTemplate.opsForHash().entries(keyFor(jobId));
        Object typeValue = values.get(OUTCOME_TYPE);
        if (typeValue == null) {
            return Optional.empty();
        }
        AnalysisJobOutcome.Type type = AnalysisJobOutcome.Type.valueOf(typeValue.toString());
        return Optional.of(switch (type) {
            case RESULT -> AnalysisJobOutcome.completed(
                    requiredValue(values, RESULT_JSON),
                    optionalValue(values, USAGE_JSON)
            );
            case FAILURE -> AnalysisJobOutcome.failed(
                    requiredValue(values, ERROR_CODE),
                    requiredValue(values, ERROR_DETAIL),
                    optionalValue(values, USAGE_JSON)
            );
        });
    }

    /** 실행별 Namespace가 포함된 작업 Key */
    String keyFor(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Analysis job id is required");
        }
        return keyPrefix + jobId;
    }

    /** Redis Hash 저장 인수 변환 */
    private Object[] jobArguments(AnalysisJob job) {
        return new Object[] {
                job.id(),
                job.owner().type().name(),
                job.owner().accessKeyHash(),
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
    private Object[] replaceArguments(long expectedVersion, AnalysisJob job) {
        Object[] jobArguments = jobArguments(job);
        Object[] arguments = new Object[jobArguments.length + 1];
        arguments[0] = Long.toString(expectedVersion);
        System.arraycopy(jobArguments, 0, arguments, 1, jobArguments.length);
        return arguments;
    }

    /** 종료 결과 포함 조건부 교체 인수 변환 */
    private Object[] outcomeArguments(
            long expectedVersion,
            AnalysisJob job,
            AnalysisJobOutcome outcome
    ) {
        Object[] replaceArguments = replaceArguments(expectedVersion, job);
        Object[] arguments = new Object[replaceArguments.length + 5];
        System.arraycopy(replaceArguments, 0, arguments, 0, replaceArguments.length);
        arguments[replaceArguments.length] = outcome.type().name();
        arguments[replaceArguments.length + 1] = valueOrEmpty(outcome.resultJson());
        arguments[replaceArguments.length + 2] = valueOrEmpty(outcome.errorCode());
        arguments[replaceArguments.length + 3] = valueOrEmpty(outcome.errorDetail());
        arguments[replaceArguments.length + 4] = valueOrEmpty(outcome.usageJson());
        return arguments;
    }

    /** Redis Hash의 Domain 상태 복원 */
    private AnalysisJob toJob(Map<Object, Object> values) {
        return new AnalysisJob(
                requiredValue(values, ID),
                new AnalysisJobOwner(
                        AnalysisJobOwnerType.valueOf(requiredValue(values, OWNER_TYPE)),
                        requiredValue(values, OWNER_ACCESS_KEY_HASH)
                ),
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

    /** 빈 값의 Optional Hash 필드 변환 */
    private String optionalValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    /** Redis 인수용 빈 문자열 변환 */
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** ISO-8601 시각 복원 */
    private Instant instant(Map<Object, Object> values, String field) {
        return Instant.parse(requiredValue(values, field));
    }
}
