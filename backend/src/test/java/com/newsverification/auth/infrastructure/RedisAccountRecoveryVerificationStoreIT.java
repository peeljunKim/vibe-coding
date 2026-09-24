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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 다섯 번째 올바른 인증번호 비교 허용 */
    @Test
    void allowsCorrectCodeOnFifthAttempt() {
        RedisAccountRecoveryVerificationStore store = storeAt(now);
        store.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(store.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "000000"))
                    .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.INVALID);
        }

        assertThat(store.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.VERIFIED);
    }

    /** 병렬 요청의 최대 다섯 번 Hash 비교 제한 */
    @Test
    void reservesAtMostFiveConcurrentAttempts() throws Exception {
        CountingPasswordEncoder encoder = new CountingPasswordEncoder();
        RedisAccountRecoveryVerificationStore store = storeAt(now, encoder);
        store.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AccountRecoveryVerificationStore.VerificationResult>> results = new ArrayList<>();

        try {
            for (int index = 0; index < 10; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.verify(
                            AccountRecoveryVerificationStore.Purpose.PASSWORD,
                            LOOKUP_KEY,
                            "000000"
                    );
                }));
            }
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<AccountRecoveryVerificationStore.VerificationResult> completed = new ArrayList<>();
            for (Future<AccountRecoveryVerificationStore.VerificationResult> result : results) {
                completed.add(result.get(5, TimeUnit.SECONDS));
            }
            assertThat(encoder.matchCount()).isEqualTo(5);
            assertThat(completed).filteredOn(result -> result == AccountRecoveryVerificationStore.VerificationResult.INVALID)
                    .hasSize(4);
            assertThat(completed).filteredOn(result -> result == AccountRecoveryVerificationStore.VerificationResult.BLOCKED)
                    .hasSize(6);
        } finally {
            executor.shutdownNow();
        }
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

    /** 이전 메일 실패 보상이 새 인증번호를 삭제하지 않음 */
    @Test
    void keepsNewCodeWhenOlderDeliveryFails() {
        RedisAccountRecoveryVerificationStore initialStore = storeAt(now);
        initialStore.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "482916");

        RedisAccountRecoveryVerificationStore laterStore = storeAt(now.plusSeconds(61));
        laterStore.issue(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "135790");
        laterStore.consumeIfCodeMatches(
                AccountRecoveryVerificationStore.Purpose.PASSWORD,
                LOOKUP_KEY,
                "482916"
        );

        assertThat(laterStore.verify(AccountRecoveryVerificationStore.Purpose.PASSWORD, LOOKUP_KEY, "135790"))
                .isEqualTo(AccountRecoveryVerificationStore.VerificationResult.VERIFIED);
    }

    private RedisAccountRecoveryVerificationStore storeAt(Instant instant) {
        return storeAt(instant, PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    private RedisAccountRecoveryVerificationStore storeAt(Instant instant, PasswordEncoder encoder) {
        return new RedisAccountRecoveryVerificationStore(
                redisTemplate,
                encoder,
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

    /** 병렬 Hash 비교 횟수 기록용 Encoder */
    private static final class CountingPasswordEncoder implements PasswordEncoder {

        private final AtomicInteger matches = new AtomicInteger();

        @Override
        public String encode(CharSequence rawPassword) {
            return "test-digest:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matches.incrementAndGet();
            return encodedPassword.equals(encode(rawPassword));
        }

        int matchCount() {
            return matches.get();
        }
    }
}
