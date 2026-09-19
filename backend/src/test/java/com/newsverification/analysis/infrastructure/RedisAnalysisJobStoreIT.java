/* Redis 분석 작업 저장소 통합 검증 */
package com.newsverification.analysis.infrastructure;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트 전용 Redis의 TTL과 조건부 전환 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisAnalysisJobStoreIT {

    private static final RedisScript<Long> REDIS_TIME_SCRIPT = new DefaultRedisScript<>("""
            local currentTime = redis.call('TIME')
            return currentTime[1] * 1000 + math.floor(currentTime[2] / 1000)
            """, Long.class);
    private static final AnalysisJobOwner MEMBER_OWNER = new AnalysisJobOwner(
            AnalysisJobOwnerType.MEMBER,
            "member-owner-key"
    );

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAnalysisJobStore store;
    private final Set<String> createdKeys = new HashSet<>();

    /** 테스트 전용 Redis 연결과 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        String host = requiredEnvironment("REDIS_TEST_HOST");
        int port = Integer.parseInt(requiredEnvironment("REDIS_TEST_PORT"));
        String password = requiredEnvironment("REDIS_TEST_PASSWORD");
        String namespace = requiredEnvironment("REDIS_TEST_NAMESPACE");

        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(host, port);
        redis.setPassword(RedisPassword.of(password));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        connectionFactory = new LettuceConnectionFactory(redis, client);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisAnalysisJobStore(redisTemplate, namespace);
    }

    /** 생성한 Namespace Key만 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null && !createdKeys.isEmpty()) {
            redisTemplate.delete(createdKeys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 실행 중 작업 저장과 5분 물리 TTL */
    @Test
    void storesProcessingJobWithFiveMinuteTtl() {
        String jobId = track("processing-job");
        AnalysisJob job = AnalysisJob.queued(jobId, MEMBER_OWNER, Instant.now());

        assertThat(store.create(job)).isTrue();

        assertThat(store.findById(jobId)).contains(job);
        assertThat(Duration.between(job.acceptedAt(), job.expiresAt()))
                .isEqualTo(Duration.ofMinutes(5));
        assertPhysicalExpiryMatches(job);
        assertThat(store.create(job)).isFalse();
    }

    /** 종료 상태 전환과 30분 TTL 및 읽기 무연장 */
    @Test
    void storesTerminalJobWithThirtyMinuteTtlWithoutReadExtension() {
        String jobId = track("terminal-job");
        MutableClock clock = new MutableClock(Instant.now());
        AnalysisJobLifecycleService service = new AnalysisJobLifecycleService(store, clock);
        service.accept(jobId, MEMBER_OWNER);
        service.advance(jobId, AnalysisJobStage.CHECKING_ARTICLE);
        service.advance(jobId, AnalysisJobStage.SEARCHING_EVIDENCE);
        service.advance(jobId, AnalysisJobStage.GENERATING_RESULT);

        AnalysisJob completed = service.complete(jobId);
        Long ttlBeforeRead = redisTemplate.getExpire(store.keyFor(jobId), TimeUnit.MILLISECONDS);
        AnalysisJob polled = service.poll(jobId, MEMBER_OWNER).orElseThrow();
        Long ttlAfterRead = redisTemplate.getExpire(store.keyFor(jobId), TimeUnit.MILLISECONDS);

        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(Duration.between(clock.instant(), completed.expiresAt()))
                .isEqualTo(Duration.ofMinutes(30));
        assertPhysicalExpiryMatches(completed);
        assertThat(polled).isEqualTo(completed);
        assertThat(ttlAfterRead).isLessThanOrEqualTo(ttlBeforeRead);
    }

    /** 오래된 Version의 조건부 전환 거절 */
    @Test
    void rejectsStaleVersionReplacement() {
        String jobId = track("stale-version-job");
        AnalysisJob queued = AnalysisJob.queued(jobId, MEMBER_OWNER, Instant.now());
        AnalysisJob checking = queued.advanceTo(AnalysisJobStage.CHECKING_ARTICLE, Instant.now());

        assertThat(store.create(queued)).isTrue();
        assertThat(store.replace(jobId, queued.version(), checking)).isTrue();
        assertThat(store.replace(jobId, queued.version(), checking)).isFalse();
        assertThat(store.findById(jobId)).contains(checking);
    }

    /** Redis 상태 교체를 통한 작업 소유권 변경 차단 */
    @Test
    void rejectsOwnerChangeDuringStateReplacement() {
        String jobId = track("owner-change-job");
        Instant acceptedAt = Instant.now();
        AnalysisJob original = AnalysisJob.queued(jobId, MEMBER_OWNER, acceptedAt);
        AnalysisJob differentOwner = AnalysisJob.queued(
                jobId,
                new AnalysisJobOwner(AnalysisJobOwnerType.GUEST, "guest-owner-key"),
                acceptedAt
        );

        assertThat(store.create(original)).isTrue();
        assertThat(store.replace(jobId, original.version(), differentOwner)).isFalse();
        assertThat(store.findById(jobId)).contains(original);
    }

    /** 동시 완료와 실패 중 단일 종료 전환 */
    @Test
    void allowsOnlyOneConcurrentTerminalTransition() throws Exception {
        String jobId = track("concurrent-terminal-job");
        Instant now = Instant.now();
        AnalysisJob generating = AnalysisJob.queued(jobId, MEMBER_OWNER, now)
                .advanceTo(AnalysisJobStage.CHECKING_ARTICLE, now)
                .advanceTo(AnalysisJobStage.SEARCHING_EVIDENCE, now)
                .advanceTo(AnalysisJobStage.GENERATING_RESULT, now);
        AnalysisJob completed = generating.complete(now);
        AnalysisJob failed = generating.fail(now);
        assertThat(store.create(generating)).isTrue();

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var completeFuture = executor.submit(() -> replaceAfterStart(jobId, generating.version(), completed, ready, start));
            var failFuture = executor.submit(() -> replaceAfterStart(jobId, generating.version(), failed, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(Set.of(completeFuture.get(), failFuture.get()))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(store.findById(jobId).orElseThrow().status())
                    .isIn(AnalysisJobStatus.COMPLETED, AnalysisJobStatus.FAILED);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 결과와 종료 상태의 단일 원자 전환 */
    @Test
    void storesCompletedOutcomeWithTerminalTransition() {
        String jobId = track("completed-outcome-job");
        Instant now = Instant.now();
        AnalysisJob generating = AnalysisJob.queued(jobId, MEMBER_OWNER, now)
                .advanceTo(AnalysisJobStage.CHECKING_ARTICLE, now)
                .advanceTo(AnalysisJobStage.SEARCHING_EVIDENCE, now)
                .advanceTo(AnalysisJobStage.GENERATING_RESULT, now);
        AnalysisJob completed = generating.complete(now.plusSeconds(1));
        AnalysisJobOutcome outcome = AnalysisJobOutcome.completed(
                "{\"overallStatus\":\"CAUTION\"}",
                "{\"limit\":5,\"used\":1,\"remaining\":4,\"charged\":true}"
        );
        assertThat(store.create(generating)).isTrue();

        assertThat(store.replaceWithOutcome(
                jobId,
                generating.version(),
                completed,
                outcome
        )).isTrue();

        assertThat(store.findById(jobId)).contains(completed);
        assertThat(store.findOutcome(jobId)).contains(outcome);
        assertPhysicalExpiryMatches(completed);
    }

    /** 종료 이후 늦은 실패 결과 덮어쓰기 차단 */
    @Test
    void rejectsStaleFailureOutcomeAfterCompletion() {
        String jobId = track("stale-outcome-job");
        Instant now = Instant.now();
        AnalysisJob generating = AnalysisJob.queued(jobId, MEMBER_OWNER, now)
                .advanceTo(AnalysisJobStage.CHECKING_ARTICLE, now)
                .advanceTo(AnalysisJobStage.SEARCHING_EVIDENCE, now)
                .advanceTo(AnalysisJobStage.GENERATING_RESULT, now);
        AnalysisJob completed = generating.complete(now.plusSeconds(1));
        AnalysisJob failed = generating.fail(now.plusSeconds(1));
        AnalysisJobOutcome completedOutcome = AnalysisJobOutcome.completed("{}", "{}");
        AnalysisJobOutcome failedOutcome = AnalysisJobOutcome.failed(
                "ANALYSIS_FAILED",
                "분석하지 못했습니다.",
                null
        );
        assertThat(store.create(generating)).isTrue();
        assertThat(store.replaceWithOutcome(
                jobId,
                generating.version(),
                completed,
                completedOutcome
        )).isTrue();

        assertThat(store.replaceWithOutcome(
                jobId,
                generating.version(),
                failed,
                failedOutcome
        )).isFalse();
        assertThat(store.findById(jobId)).contains(completed);
        assertThat(store.findOutcome(jobId)).contains(completedOutcome);
    }

    /** 90초 경계 이후 완료 결과 폐기 */
    @Test
    void discardsCompletionAtDeadline() {
        String jobId = track("late-result-job");
        Instant acceptedAt = Instant.now();
        MutableClock clock = new MutableClock(acceptedAt);
        AnalysisJobLifecycleService service = new AnalysisJobLifecycleService(store, clock);
        service.accept(jobId, MEMBER_OWNER);
        service.advance(jobId, AnalysisJobStage.CHECKING_ARTICLE);
        service.advance(jobId, AnalysisJobStage.SEARCHING_EVIDENCE);
        AnalysisJob generating = service.advance(jobId, AnalysisJobStage.GENERATING_RESULT);

        clock.setInstant(acceptedAt.plusSeconds(90));
        AnalysisJob unchanged = service.complete(jobId);

        assertThat(unchanged).isEqualTo(generating);
        assertThat(store.findById(jobId)).contains(generating);
    }

    /** Redis 물리 만료 후 작업 조회 불가 */
    @Test
    void removesJobAtPhysicalExpiry() throws Exception {
        String jobId = track("expiring-job");
        Long redisTimeMillis = redisTemplate.execute(REDIS_TIME_SCRIPT, List.of());
        assertThat(redisTimeMillis).isNotNull();
        Instant now = Instant.ofEpochMilli(redisTimeMillis);
        AnalysisJob expiring = new AnalysisJob(
                jobId,
                MEMBER_OWNER,
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.QUEUED,
                now,
                now.plusSeconds(90),
                now.plusMillis(800),
                0
        );

        assertThat(store.create(expiring)).isTrue();
        assertThat(store.findById(jobId)).contains(expiring);

        waitUntilMissing(jobId, Duration.ofSeconds(3));

        assertThat(store.findById(jobId)).isEmpty();
    }

    /** Redis 연결 실패 시 신규 분석 접수 차단 */
    @Test
    void blocksAcceptanceWhenRedisIsUnavailable() {
        RedisStandaloneConfiguration unavailableRedis = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .build();
        LettuceConnectionFactory unavailableFactory = new LettuceConnectionFactory(unavailableRedis, client);
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailableTemplate = new StringRedisTemplate(unavailableFactory);
        unavailableTemplate.afterPropertiesSet();
        AnalysisJobLifecycleService unavailableService = new AnalysisJobLifecycleService(
                new RedisAnalysisJobStore(unavailableTemplate, "test:unavailable"),
                Clock.systemUTC()
        );

        try {
            assertThatThrownBy(() -> unavailableService.accept("blocked-job", MEMBER_OWNER))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            unavailableFactory.destroy();
        }
    }

    /** 동시 시작 신호 이후 조건부 교체 */
    private boolean replaceAfterStart(
            String jobId,
            long expectedVersion,
            AnalysisJob updatedJob,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent transition start timeout");
        }
        return store.replace(jobId, expectedVersion, updatedJob);
    }

    /** 제한 시간 안의 Redis 만료 대기 */
    private void waitUntilMissing(String jobId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline) && store.findById(jobId).isPresent()) {
            Thread.sleep(50);
        }
    }

    /** Redis 기준 절대 만료 시각 일치 확인 */
    private void assertPhysicalExpiryMatches(AnalysisJob job) {
        Long redisTimeMillis = redisTemplate.execute(REDIS_TIME_SCRIPT, List.of());
        Long physicalTtlMillis = redisTemplate.getExpire(store.keyFor(job.id()), TimeUnit.MILLISECONDS);
        assertThat(redisTimeMillis).isNotNull();
        assertThat(physicalTtlMillis).isNotNull();

        long expectedTtlMillis = job.expiresAt().toEpochMilli() - redisTimeMillis;
        assertThat(physicalTtlMillis).isBetween(expectedTtlMillis - 250L, expectedTtlMillis + 50L);
    }

    /** 테스트 정리 대상 Key 등록 */
    private String track(String jobId) {
        createdKeys.add(store.keyFor(jobId));
        return jobId;
    }

    /** 필수 테스트 환경 변수 조회 */
    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    /** 테스트 제어용 변경 가능 시계 */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }
    }
}
