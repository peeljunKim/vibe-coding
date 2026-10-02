/* Redis 분석 접수 요청 제한 통합 검증 */
package com.newsverification.analysis.infrastructure;

import com.newsverification.analysis.application.AnalysisRequestRateLimitExceededException;
import com.newsverification.analysis.application.AnalysisRequestRateLimiter;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트 전용 Redis의 기능별 고정 시간 요청 제한 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisAnalysisRequestRateLimiterIT {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAnalysisRequestRateLimiter limiter;
    private String namespace;

    /** 테스트 Redis 연결과 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        String host = requiredEnvironment("REDIS_TEST_HOST");
        int port = Integer.parseInt(requiredEnvironment("REDIS_TEST_PORT"));
        String password = requiredEnvironment("REDIS_TEST_PASSWORD");
        namespace = requiredEnvironment("REDIS_TEST_NAMESPACE") + ":request-limit";

        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(host, port);
        redis.setPassword(RedisPassword.of(password));
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        connectionFactory = new LettuceConnectionFactory(redis, client);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        limiter = new RedisAnalysisRequestRateLimiter(redisTemplate, namespace, 5, Duration.ofMinutes(1));
    }

    /** 테스트 Namespace Key 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(namespace + ":analysis-request-rate:v1:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 1분 안의 다섯 요청 허용과 여섯 번째 차단 */
    @Test
    void allowsFiveRequestsAndRejectsTheSixth() {
        for (int request = 1; request <= 5; request++) {
            assertThatCode(() -> limiter.acquire(
                    AnalysisRequestRateLimiter.Feature.HEALTH, List.of("member-hmac")))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH, List.of("member-hmac")))
                .isInstanceOf(AnalysisRequestRateLimitExceededException.class);
    }

    /** 건강과 제목 기능의 독립 요청 제한 */
    @Test
    void separatesHealthAndHeadlineNamespaces() {
        exhaust(AnalysisRequestRateLimiter.Feature.HEALTH, "shared-hmac");

        assertThatCode(() -> limiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEADLINE, List.of("shared-hmac")))
                .doesNotThrowAnyException();
    }

    /** 서로 다른 회원과 비회원 비식별값의 독립 요청 제한 */
    @Test
    void separatesMemberAndGuestIdentifiers() {
        exhaust(AnalysisRequestRateLimiter.Feature.HEALTH, "member-hmac");

        assertThatCode(() -> limiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH, List.of("guest-hmac")))
                .doesNotThrowAnyException();
    }

    /** 비회원 식별 신호 하나만 바꾼 요청의 우회 차단 */
    @Test
    void synchronizesCountsAcrossGuestIdentifierKeys() {
        for (int request = 1; request <= 4; request++) {
            limiter.acquire(
                    AnalysisRequestRateLimiter.Feature.HEALTH,
                    List.of("browser-hmac", "ip-hmac")
            );
        }
        limiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH,
                List.of("new-browser-hmac", "ip-hmac")
        );

        assertThatThrownBy(() -> limiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH,
                List.of("new-browser-hmac", "new-ip-hmac")
        )).isInstanceOf(AnalysisRequestRateLimitExceededException.class);
    }

    /** 제한 시간 만료 뒤 요청 허용 */
    @Test
    void permitsRequestsAfterWindowExpires() throws InterruptedException {
        RedisAnalysisRequestRateLimiter shortWindowLimiter = new RedisAnalysisRequestRateLimiter(
                redisTemplate, namespace, 1, Duration.ofMillis(150));
        shortWindowLimiter.acquire(AnalysisRequestRateLimiter.Feature.HEALTH, List.of("expiring-hmac"));

        assertThatThrownBy(() -> shortWindowLimiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH, List.of("expiring-hmac")))
                .isInstanceOf(AnalysisRequestRateLimitExceededException.class);

        Thread.sleep(250);

        assertThatCode(() -> shortWindowLimiter.acquire(
                AnalysisRequestRateLimiter.Feature.HEALTH, List.of("expiring-hmac")))
                .doesNotThrowAnyException();
    }

    /** 요청 식별값 원문을 Redis Key에서 제외 */
    @Test
    void storesOnlyDigestedIdentifiersInKeys() {
        limiter.acquire(AnalysisRequestRateLimiter.Feature.HEALTH, List.of("private-member-hmac"));

        Set<String> keys = redisTemplate.keys(namespace + ":analysis-request-rate:v1:*");

        assertThat(keys).isNotNull().hasSize(1);
        assertThat(keys.iterator().next()).doesNotContain("private-member-hmac");
    }

    /** 지정 기능과 식별값의 허용 횟수 소진 */
    private void exhaust(AnalysisRequestRateLimiter.Feature feature, String identifierKey) {
        for (int request = 1; request <= 5; request++) {
            limiter.acquire(feature, List.of(identifierKey));
        }
    }

    /** 필수 테스트 환경값 조회 */
    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
