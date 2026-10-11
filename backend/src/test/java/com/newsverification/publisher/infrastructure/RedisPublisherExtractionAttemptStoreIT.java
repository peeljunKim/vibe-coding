/* Redis 언론사 추출 실패율 Sliding Window 통합 검증 */
package com.newsverification.publisher.infrastructure;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 테스트 전용 Redis의 10회·70% 자동 중단 경계 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisPublisherExtractionAttemptStoreIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private String namespace;

    /** 테스트 Redis 연결과 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        String host = requiredEnvironment("REDIS_TEST_HOST");
        int port = Integer.parseInt(requiredEnvironment("REDIS_TEST_PORT"));
        String password = requiredEnvironment("REDIS_TEST_PASSWORD");
        namespace = requiredEnvironment("REDIS_TEST_NAMESPACE") + ":publisher-health";

        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(host, port);
        redis.setPassword(RedisPassword.of(password));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        connectionFactory = new LettuceConnectionFactory(redis, client);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    /** 테스트 Namespace Key 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(namespace + ":publisher-extraction-health:v1:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 열 번 중 일곱 번 실패한 언론사 중단 판정 */
    @Test
    void pausesAtTenAttemptsAndSeventyPercentFailures() {
        RedisPublisherExtractionAttemptStore store = store(Duration.ofHours(1));

        for (int attempt = 1; attempt <= 6; attempt++) {
            assertThat(store.record("news.example", true)).isFalse();
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(store.record("news.example", false)).isFalse();
        }

        assertThat(store.record("news.example", true)).isTrue();
    }

    /** 실패율 70% 미만 상태 유지 */
    @Test
    void keepsPublisherActiveBelowFailureRate() {
        RedisPublisherExtractionAttemptStore store = store(Duration.ofHours(1));

        for (int attempt = 1; attempt <= 6; attempt++) {
            store.record("news.example", true);
        }
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(store.record("news.example", false)).isFalse();
        }
    }

    /** 시간 창 만료 뒤 이전 시도 제외 */
    @Test
    void excludesAttemptsAfterWindowExpires() throws InterruptedException {
        RedisPublisherExtractionAttemptStore store = store(Duration.ofMillis(150));
        for (int attempt = 1; attempt <= 9; attempt++) {
            store.record("news.example", true);
        }

        Thread.sleep(250);

        assertThat(store.record("news.example", true)).isFalse();
    }

    /** 언론사별 독립 집계 */
    @Test
    void separatesPublisherNamespaces() {
        RedisPublisherExtractionAttemptStore store = store(Duration.ofHours(1));
        for (int attempt = 1; attempt <= 9; attempt++) {
            store.record("first.example", true);
        }

        assertThat(store.record("second.example", true)).isFalse();
    }

    private RedisPublisherExtractionAttemptStore store(Duration window) {
        return new RedisPublisherExtractionAttemptStore(redisTemplate, namespace, window, 10, 70);
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
