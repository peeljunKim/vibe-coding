/* Redis Streams 건강 분석 Queue 통합 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisTask;
import com.newsverification.health.application.HealthAnalysisUserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Redis Stream의 수용량과 단일 소비 보장 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisHealthAnalysisQueueIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisHealthAnalysisQueue queue;

    /** 테스트 Redis와 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(
                requiredEnvironment("REDIS_TEST_HOST"),
                Integer.parseInt(requiredEnvironment("REDIS_TEST_PORT"))
        );
        redis.setPassword(RedisPassword.of(requiredEnvironment("REDIS_TEST_PASSWORD")));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        connectionFactory = new LettuceConnectionFactory(redis, client);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        queue = new RedisHealthAnalysisQueue(
                redisTemplate,
                requiredEnvironment("REDIS_TEST_NAMESPACE")
        );
    }

    /** 테스트 Namespace 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete(List.of(queue.streamKey(), queue.workerLockKey()));
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 대기 작업 20개 이후 추가 접수 거절 */
    @Test
    void rejectsTwentyFirstWaitingTask() {
        for (int index = 1; index <= 20; index++) {
            assertThat(queue.enqueue(task("job-" + index))).isTrue();
        }

        assertThat(queue.enqueue(task("job-21"))).isFalse();
        assertThat(redisTemplate.opsForStream().size(queue.streamKey())).isEqualTo(20L);
    }

    /** 소비한 작업 제거와 자동 재전달 차단 */
    @Test
    void removesDequeuedTaskWithoutAutomaticRetry() {
        HealthAnalysisTask task = task("job-once");
        assertThat(queue.enqueue(task)).isTrue();

        assertThat(queue.take()).contains(task);
        assertThat(queue.take()).isEmpty();
        assertThat(redisTemplate.opsForStream().size(queue.streamKey())).isZero();
    }

    /** 전역 Worker Lease의 단일 소유자 보장 */
    @Test
    void allowsOnlyOneWorkerLease() {
        assertThat(queue.tryAcquireWorker("worker-a", Duration.ofSeconds(95))).isTrue();
        assertThat(queue.tryAcquireWorker("worker-b", Duration.ofSeconds(95))).isFalse();

        queue.releaseWorker("worker-b");
        assertThat(queue.tryAcquireWorker("worker-b", Duration.ofSeconds(95))).isFalse();

        queue.releaseWorker("worker-a");
        assertThat(queue.tryAcquireWorker("worker-b", Duration.ofSeconds(95))).isTrue();
    }

    /** 고정 Queue 작업 구성 */
    private HealthAnalysisTask task(String jobId) {
        return new HealthAnalysisTask(
                jobId,
                "https://news.example/article/" + jobId,
                HealthAnalysisUserType.GUEST,
                List.of("guest-cookie-key", "guest-ip-key")
        );
    }

    /** 필수 통합 테스트 환경 변수 조회 */
    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
