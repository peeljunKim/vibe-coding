/* Redis 건강 분야 판별 실패 이용량 통합 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisUsageSubject;
import com.newsverification.health.application.HealthAnalysisUserType;
import com.newsverification.health.application.HealthDailyUsageLimitExceededException;
import com.newsverification.health.application.HealthTopicFailureUsageResult;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트 전용 Redis의 무료 처리와 차감 원자성 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisHealthTopicFailureUsagePolicyIT {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final RedisScript<Long> REDIS_TIME_SCRIPT = new DefaultRedisScript<>("""
            local currentTime = redis.call('TIME')
            return currentTime[1] * 1000 + math.floor(currentTime[2] / 1000)
            """, Long.class);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisHealthTopicFailureUsagePolicy policy;
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
        policy = new RedisHealthTopicFailureUsagePolicy(
                redisTemplate, namespace, KOREA_ZONE, Clock.systemUTC());
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

    /** 회원 첫 실패 무료와 이후 차감 및 일일 한도 */
    @Test
    void appliesMemberFreeFailureAndFiveUseLimit() {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.MEMBER, "member-key");

        assertThat(policy.recordFailure(subject).charged()).isFalse();
        for (int expected = 1; expected <= 5; expected++) {
            HealthTopicFailureUsageResult result = policy.recordFailure(subject);
            assertThat(result.charged()).isTrue();
            assertThat(result.usedCount()).isEqualTo(expected);
            assertThat(result.dailyLimit()).isEqualTo(5);
        }

        assertThatThrownBy(() -> policy.verifyCanStart(subject))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
        assertThatThrownBy(() -> policy.recordFailure(subject))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
    }

    /** 비회원 첫 실패 무료와 2회 한도 */
    @Test
    void appliesGuestFreeFailureAndTwoUseLimit() {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.GUEST, "guest-key");

        assertThat(policy.recordFailure(subject).charged()).isFalse();
        assertThat(policy.recordFailure(subject).usedCount()).isEqualTo(1);
        assertThat(policy.recordFailure(subject).usedCount()).isEqualTo(2);

        assertThatThrownBy(() -> policy.verifyCanStart(subject))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
    }

    /** 동일 식별 Key의 회원과 비회원 이용량 분리 */
    @Test
    void separatesMemberAndGuestNamespaces() {
        HealthAnalysisUsageSubject member = track(HealthAnalysisUserType.MEMBER, "shared-key");
        HealthAnalysisUsageSubject guest = track(HealthAnalysisUserType.GUEST, "shared-key");

        policy.recordFailure(member);
        policy.recordFailure(member);

        assertThat(policy.recordFailure(guest).charged()).isFalse();
        assertThat(policy.keyFor(member))
                .isNotEqualTo(policy.keyFor(guest))
                .contains(":member:")
                .doesNotContain("shared-key");
        assertThat(policy.keyFor(guest)).contains(":guest:");
    }

    /** 실제 분석 시작 시 이용 횟수 원자 차감 */
    @Test
    void chargesWhenAnalysisActuallyStarts() {
        HealthAnalysisUsageSubject subject = track(
                HealthAnalysisUserType.MEMBER,
                "analysis-start-key"
        );

        HealthTopicFailureUsageResult first = policy.recordAnalysisStart(subject);
        HealthTopicFailureUsageResult second = policy.recordAnalysisStart(subject);

        assertThat(first.charged()).isTrue();
        assertThat(first.usedCount()).isEqualTo(1);
        assertThat(second.usedCount()).isEqualTo(2);
        assertThat(second.dailyLimit()).isEqualTo(5);
    }

    /** 기존 정상 이용량과 무관한 첫 분야 실패 무료 처리 */
    @Test
    void keepsFirstTopicFailureFreeAfterExistingHealthUsage() {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.MEMBER, "existing-usage-key");
        redisTemplate.opsForHash().put(policy.keyFor(subject), "usedCount", "2");

        HealthTopicFailureUsageResult result = policy.recordFailure(subject);

        assertThat(result.charged()).isFalse();
        assertThat(result.usedCount()).isEqualTo(2);
    }

    /** 비회원 단일 신호 변경 이후 기존 이용량 승계 */
    @Test
    void preservesGuestUsageWhenOnlyOneIdentitySignalChanges() {
        HealthAnalysisUsageSubject original = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-a", "ip-a")
        );
        HealthAnalysisUsageSubject changedCookie = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-b", "ip-a")
        );
        HealthAnalysisUsageSubject changedIp = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-b", "ip-b")
        );

        assertThat(policy.recordFailure(original).charged()).isFalse();
        assertThat(policy.recordFailure(changedCookie).usedCount()).isEqualTo(1);
        assertThat(policy.recordFailure(changedIp).usedCount()).isEqualTo(2);
        assertThatThrownBy(() -> policy.verifyCanStart(changedIp))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
    }

    /** 한도 도달 뒤 순차 신호 변경의 이용량 우회 차단 */
    @Test
    void blocksSequentialGuestSignalChangesAfterLimit() {
        HealthAnalysisUsageSubject original = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-a", "ip-a")
        );
        HealthAnalysisUsageSubject changedCookie = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-b", "ip-a")
        );
        HealthAnalysisUsageSubject changedIpAfterRejection = track(
                HealthAnalysisUserType.GUEST,
                List.of("cookie-b", "ip-b")
        );
        policy.recordFailure(original);
        policy.recordFailure(original);
        policy.recordFailure(original);

        assertThatThrownBy(() -> policy.verifyCanStart(changedCookie))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
        assertThatThrownBy(() -> policy.verifyCanStart(changedIpAfterRejection))
                .isInstanceOf(HealthDailyUsageLimitExceededException.class);
    }

    /** 동시 첫 실패의 단일 무료 처리 */
    @Test
    void grantsOnlyOneFreeFailureDuringConcurrentRequests() throws Exception {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.MEMBER, "concurrent-key");
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> recordAfterStart(subject, ready, start));
            var second = executor.submit(() -> recordAfterStart(subject, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            HealthTopicFailureUsageResult firstResult = first.get();
            HealthTopicFailureUsageResult secondResult = second.get();
            assertThat(Set.of(firstResult.charged(), secondResult.charged()))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(Set.of(firstResult.usedCount(), secondResult.usedCount()))
                    .containsExactlyInAnyOrder(0, 1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 동시 한도 경계의 단일 차감과 초과 차단 */
    @Test
    void allowsOnlyOneChargeAtConcurrentLimitBoundary() throws Exception {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.MEMBER, "limit-race-key");
        policy.recordFailure(subject);
        for (int ignored = 0; ignored < 4; ignored++) {
            policy.recordFailure(subject);
        }

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> recordOutcomeAfterStart(subject, ready, start));
            var second = executor.submit(() -> recordOutcomeAfterStart(subject, ready, start));
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(Set.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("CHARGED:5", "LIMIT");
        } finally {
            executor.shutdownNow();
        }
    }

    /** 한국시간 자정 만료와 한도 조회의 TTL 무연장 */
    @Test
    void expiresAtNextKoreaMidnightWithoutAvailabilityExtension() {
        HealthAnalysisUsageSubject subject = track(HealthAnalysisUserType.MEMBER, "ttl-key");
        Instant expectedExpiry = Instant.now()
                .atZone(KOREA_ZONE)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(KOREA_ZONE)
                .toInstant();
        policy.recordFailure(subject);
        String key = policy.keyFor(subject);
        Long ttlBeforeRead = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        policy.verifyCanStart(subject);
        Long redisTimeMillis = redisTemplate.execute(REDIS_TIME_SCRIPT, List.of());
        Long ttlAfterRead = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertThat(redisTimeMillis).isNotNull();
        assertThat(ttlAfterRead).isNotNull();
        long expectedTtl = expectedExpiry.toEpochMilli() - redisTimeMillis;
        assertThat(ttlBeforeRead).isPositive();
        assertThat(ttlAfterRead).isLessThanOrEqualTo(ttlBeforeRead);
        assertThat(ttlAfterRead).isBetween(expectedTtl - 250L, expectedTtl + 50L);
    }

    /** Redis 장애 시 신규 건강 분석 접수 차단 */
    @Test
    void blocksNewHealthAnalysisWhenRedisIsUnavailable() {
        RedisStandaloneConfiguration unavailableRedis = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(200))
                .build();
        LettuceConnectionFactory unavailableFactory = new LettuceConnectionFactory(unavailableRedis, client);
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailableTemplate = new StringRedisTemplate(unavailableFactory);
        unavailableTemplate.afterPropertiesSet();
        RedisHealthTopicFailureUsagePolicy unavailablePolicy = new RedisHealthTopicFailureUsagePolicy(
                unavailableTemplate, "test:unavailable", KOREA_ZONE, Clock.systemUTC());

        try {
            assertThatThrownBy(() -> unavailablePolicy.verifyCanStart(
                    new HealthAnalysisUsageSubject(HealthAnalysisUserType.MEMBER, "blocked-key")
            )).isInstanceOf(RuntimeException.class);
        } finally {
            unavailableFactory.destroy();
        }
    }

    /** 동시 시작 신호 이후 실패 기록 */
    private HealthTopicFailureUsageResult recordAfterStart(
            HealthAnalysisUsageSubject subject,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent failure record start timeout");
        }
        return policy.recordFailure(subject);
    }

    /** 동시 한도 검사용 실패 기록 결과 */
    private String recordOutcomeAfterStart(
            HealthAnalysisUsageSubject subject,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent limit record start timeout");
        }
        try {
            return "CHARGED:" + policy.recordFailure(subject).usedCount();
        } catch (HealthDailyUsageLimitExceededException exception) {
            return "LIMIT";
        }
    }

    /** 테스트 정리 대상 Key 등록 */
    private HealthAnalysisUsageSubject track(HealthAnalysisUserType type, String identifierKey) {
        HealthAnalysisUsageSubject subject = new HealthAnalysisUsageSubject(type, identifierKey);
        createdKeys.addAll(policy.keysFor(subject));
        return subject;
    }

    /** 복수 테스트 식별 Key 등록 */
    private HealthAnalysisUsageSubject track(HealthAnalysisUserType type, List<String> identifierKeys) {
        HealthAnalysisUsageSubject subject = new HealthAnalysisUsageSubject(type, identifierKeys);
        createdKeys.addAll(policy.keysFor(subject));
        return subject;
    }

    /** 필수 테스트 환경 변수 조회 */
    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
