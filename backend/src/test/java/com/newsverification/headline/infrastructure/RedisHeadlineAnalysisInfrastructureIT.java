/* Redis 기사 제목 분석 Queue와 이용량 통합 검증 */
package com.newsverification.headline.infrastructure;

import com.newsverification.headline.application.HeadlineAnalysisTask;
import com.newsverification.headline.application.HeadlineAnalysisUsageSubject;
import com.newsverification.headline.application.HeadlineAnalysisUserType;
import com.newsverification.headline.application.HeadlineDailyUsageLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 제목 전용 Namespace와 Queue·일일 한도 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisHeadlineAnalysisInfrastructureIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisHeadlineAnalysisQueue queue;
    private RedisHeadlineAnalysisUsagePolicy usagePolicy;
    private final Set<String> createdKeys = new HashSet<>();

    /** 테스트 Redis와 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        String namespace = requiredEnvironment("REDIS_TEST_NAMESPACE");
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
        queue = new RedisHeadlineAnalysisQueue(redisTemplate, namespace);
        usagePolicy = new RedisHeadlineAnalysisUsagePolicy(
                redisTemplate, namespace, ZoneId.of("Asia/Seoul"), Clock.systemUTC()
        );
    }

    /** 테스트 Namespace 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            createdKeys.add(queue.streamKey());
            createdKeys.add(queue.workerLockKey());
            redisTemplate.delete(createdKeys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 제목 Queue의 단일 소비와 전용 Namespace */
    @Test
    void enqueuesAndConsumesHeadlineTaskOnce() {
        HeadlineAnalysisTask task = new HeadlineAnalysisTask(
                "headline-1",
                "https://news.example/general",
                HeadlineAnalysisUserType.GUEST,
                List.of("cookie-key", "ip-key")
        );

        assertThat(queue.streamKey()).contains(":headline-analysis:");
        assertThat(queue.enqueue(task)).isTrue();
        assertThat(queue.take()).contains(task);
        assertThat(queue.take()).isEmpty();
    }

    /** 비회원 5회와 회원 10회의 독립 제목 한도 */
    @Test
    void appliesHeadlineSpecificDailyLimits() {
        HeadlineAnalysisUsageSubject guest = track(
                HeadlineAnalysisUserType.GUEST, List.of("guest-cookie", "guest-ip")
        );
        HeadlineAnalysisUsageSubject member = track(
                HeadlineAnalysisUserType.MEMBER, List.of("member")
        );

        for (int expected = 1; expected <= 5; expected++) {
            assertThat(usagePolicy.recordAnalysisStart(guest).usedCount()).isEqualTo(expected);
        }
        assertThat(usagePolicy.currentUsage(guest).usedCount()).isEqualTo(5);
        assertThatThrownBy(() -> usagePolicy.verifyCanStart(guest))
                .isInstanceOf(HeadlineDailyUsageLimitExceededException.class);

        for (int expected = 1; expected <= 10; expected++) {
            assertThat(usagePolicy.recordAnalysisStart(member).usedCount()).isEqualTo(expected);
        }
        assertThat(usagePolicy.currentUsage(member).usedCount()).isEqualTo(10);
        assertThatThrownBy(() -> usagePolicy.recordAnalysisStart(member))
                .isInstanceOf(HeadlineDailyUsageLimitExceededException.class);
    }

    /** 테스트 정리 대상 이용량 Key 등록 */
    private HeadlineAnalysisUsageSubject track(
            HeadlineAnalysisUserType type,
            List<String> identifierKeys
    ) {
        HeadlineAnalysisUsageSubject subject = new HeadlineAnalysisUsageSubject(type, identifierKeys);
        createdKeys.addAll(usagePolicy.keysFor(subject));
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
