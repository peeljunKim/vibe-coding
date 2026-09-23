/* Docker Redis 계정 복구 인증 상태 통합 검증 */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryException;
import com.newsverification.auth.application.AccountRecoveryVerificationStore;
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

/** 인증번호 평문 미저장과 계정 복구 제한 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisAccountRecoveryVerificationStoreIT {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String LOOKUP_KEY = "test-email-hmac";

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
                LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(2)).build()
        );
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        keyPrefix = requiredEnvironment("REDIS_TEST_NAMESPACE")
                + ":account-recovery:" + UUID.randomUUID();
        now = Instant.now();
    }

    /** 생성한 계정 복구 Key만 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete(Set.of(codeKey(), dailyKey()));
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 인증번호 Digest와 10분 TTL 저장 검증 */
    @Test
    void storesOnlyVerificationDigestWithTenMinuteTtl() {
        RedisAccountRecoveryVerificationStore store = storeAt(now);

        store.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");

        Object storedHash = redisTemplate.opsForHash().get(codeKey(), "codeHash");
        Long ttl = redisTemplate.getExpire(codeKey(), TimeUnit.SECONDS);
        assertThat(storedHash).isNotNull();
        assertThat(storedHash.toString()).doesNotContain("482916");
        assertThat(ttl).isBetween(595L, 600L);
    }

    /** 다섯 번 실패 후 30분 인증 차단 검증 */
    @Test
    void blocksVerificationAfterFiveFailures() {
        RedisAccountRecoveryVerificationStore store = storeAt(now);
        store.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(store.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "000000"))
                    .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.INVALID);
        }
        assertThat(store.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "000000"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.BLOCKED);
        assertThat(store.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.BLOCKED);
    }

    /** 재발송 간격과 기존 인증번호 무효화 검증 */
    @Test
    void invalidatesPreviousCodeAfterAllowedResend() {
        RedisAccountRecoveryVerificationStore initialStore = storeAt(now);
        initialStore.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");

        assertThatThrownBy(() -> initialStore.issue(
                AccountRecoveryVerificationStore.Purpose.PASSWORD,
                LOOKUP_KEY,
                "135790"
        )).isInstanceOf(AccountRecoveryException.class)
                .hasMessage("RESEND_TOO_SOON");

        RedisAccountRecoveryVerificationStore laterStore = storeAt(now.plusSeconds(61));
        laterStore.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "135790");

        assertThat(laterStore.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.INVALID);
        assertThat(laterStore.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "135790"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.VERIFIED);
    }

    private RedisAccountRecoveryVerificationStore storeAt(Instant instant) {
        return new RedisAccountRecoveryVerificationStore(
                redisTemplate,
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                keyPrefix,
                KOREA,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private String codeKey() {
        return keyPrefix + ":account-recovery:v1:password:code:" + LOOKUP_KEY;
    }

    private String dailyKey() {
        return keyPrefix + ":account-recovery:v1:password:daily:" + LOOKUP_KEY + ":"
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
