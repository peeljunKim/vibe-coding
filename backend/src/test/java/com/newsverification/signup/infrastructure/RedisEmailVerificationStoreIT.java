/* Docker Redis 이메일 인증 상태 통합 검증 */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.EmailVerificationStore;
import com.newsverification.signup.application.SignupException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 인증번호 평문 미저장과 제한 정책 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisEmailVerificationStoreIT {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private String keyPrefix;
    private Instant now;

    /** 테스트 Redis 연결과 실행별 Namespace 구성 */
    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redis = new RedisStandaloneConfiguration(
                requiredEnvironment("REDIS_TEST_HOST"),
                Integer.parseInt(requiredEnvironment("REDIS_TEST_PORT"))
        );
        redis.setPassword(RedisPassword.of(requiredEnvironment("REDIS_TEST_PASSWORD")));
        connectionFactory = new LettuceConnectionFactory(
                redis,
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(2))
                        .build()
        );
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        keyPrefix = requiredEnvironment("REDIS_TEST_NAMESPACE")
                + ":signup:" + UUID.randomUUID();
        now = Instant.now();
    }

    /** 생성한 인증 Key만 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete(Set.of(codeKey(42L), dailyKey(42L)));
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 인증번호 Digest와 24시간 TTL 저장 검증 */
    @Test
    void storesOnlyVerificationDigestWithTwentyFourHourTtl() {
        RedisEmailVerificationStore store = storeAt(now);

        store.issue(42L, "482916");

        Object storedHash = redisTemplate.opsForHash().get(codeKey(42L), "codeHash");
        Long ttl = redisTemplate.getExpire(codeKey(42L), TimeUnit.SECONDS);
        assertThat(storedHash).isNotNull();
        assertThat(storedHash.toString()).doesNotContain("482916");
        assertThat(ttl).isBetween(86_395L, 86_400L);
    }

    /** 다섯 번 실패 후 30분 인증 차단 검증 */
    @Test
    void blocksVerificationAfterFiveFailures() {
        RedisEmailVerificationStore store = storeAt(now);
        store.issue(42L, "482916");

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(store.verify(42L, "000000"))
                    .isEqualTo(EmailVerificationStore.VerificationResult.INVALID);
        }
        assertThat(store.verify(42L, "000000"))
                .isEqualTo(EmailVerificationStore.VerificationResult.BLOCKED);
        assertThat(store.verify(42L, "482916"))
                .isEqualTo(EmailVerificationStore.VerificationResult.BLOCKED);
    }

    /** 재발송 간격과 기존 인증번호 무효화 검증 */
    @Test
    void invalidatesPreviousCodeAfterAllowedResend() {
        RedisEmailVerificationStore initialStore = storeAt(now);
        initialStore.issue(42L, "482916");

        assertThatThrownBy(() -> initialStore.issue(42L, "135790"))
                .isInstanceOf(SignupException.class)
                .hasMessage("RESEND_TOO_SOON");

        RedisEmailVerificationStore laterStore = storeAt(now.plusSeconds(61));
        laterStore.issue(42L, "135790");

        assertThat(laterStore.verify(42L, "482916"))
                .isEqualTo(EmailVerificationStore.VerificationResult.INVALID);
        assertThat(laterStore.verify(42L, "135790"))
                .isEqualTo(EmailVerificationStore.VerificationResult.VERIFIED);
        laterStore.consume(42L);
        assertThat(redisTemplate.hasKey(codeKey(42L))).isFalse();
    }

    private RedisEmailVerificationStore storeAt(Instant instant) {
        return new RedisEmailVerificationStore(
                redisTemplate,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                keyPrefix,
                KOREA,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private String codeKey(long userId) {
        return keyPrefix + ":signup-email:v1:code:" + userId;
    }

    private String dailyKey(long userId) {
        return keyPrefix + ":signup-email:v1:daily:" + userId + ":"
                + LocalDate.ofInstant(now, KOREA);
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }
}
