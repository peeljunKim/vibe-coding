/* Redis 기반 기능별 일일 이용량 통합 검증 */
package com.newsverification.usage.infrastructure;

import com.newsverification.headline.application.HeadlineAnalysisJobIdentityService;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.infrastructure.RedisHeadlineAnalysisUsagePolicy;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.infrastructure.RedisHealthTopicFailureUsagePolicy;
import com.newsverification.usage.application.DailyUsageService;
import com.newsverification.usage.application.DefaultDailyUsageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Redis 이용량 격리와 조회 무차감·TTL 무연장 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisDailyUsageServiceIT {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private String namespace;
    private Clock clock;
    private HealthAnalysisJobIdentityService healthIdentityService;
    private HeadlineAnalysisJobIdentityService headlineIdentityService;
    private RedisHealthTopicFailureUsagePolicy healthUsagePolicy;
    private RedisHeadlineAnalysisUsagePolicy headlineUsagePolicy;
    private DefaultDailyUsageService service;

    /** 실행별 Redis Namespace와 기존 분석 정책 구성 */
    @BeforeEach
    void setUp() {
        namespace = requiredEnvironment("REDIS_TEST_NAMESPACE") + ":daily-usage";
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

        clock = Clock.systemUTC();
        byte[] keyMaterial = "redis-daily-usage-test-key".getBytes();
        healthIdentityService = new HealthAnalysisJobIdentityService(
                clock, KOREA_ZONE, keyMaterial, new SecureRandom(), false
        );
        headlineIdentityService = new HeadlineAnalysisJobIdentityService(
                clock, KOREA_ZONE, keyMaterial, new SecureRandom(), false
        );
        healthUsagePolicy = new RedisHealthTopicFailureUsagePolicy(
                redisTemplate, namespace, KOREA_ZONE.getId()
        );
        headlineUsagePolicy = new RedisHeadlineAnalysisUsagePolicy(
                redisTemplate, namespace, KOREA_ZONE.getId()
        );
        service = new DefaultDailyUsageService(
                healthIdentityService,
                headlineIdentityService,
                healthUsagePolicy,
                headlineUsagePolicy,
                clock,
                KOREA_ZONE
        );
    }

    /** 테스트 Namespace 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(namespace + "*");
            redisTemplate.delete(keys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 기능별 이용량 격리와 조회 무차감·TTL 무연장 */
    @Test
    void readsIsolatedUsageWithoutChargeOrTtlExtension() {
        var healthIdentity = healthIdentityService.prepare(
                new HealthAnalysisJobService.Requester("member-1", null, null, "203.0.113.10")
        );
        var headlineIdentity = headlineIdentityService.prepare(
                new HeadlineAnalysisJobService.Requester("member-1", null, null, "203.0.113.10")
        );
        healthUsagePolicy.recordAnalysisStart(healthIdentity.usageSubject());
        headlineUsagePolicy.recordAnalysisStart(headlineIdentity.usageSubject());
        headlineUsagePolicy.recordAnalysisStart(headlineIdentity.usageSubject());

        Set<String> storedKeys = redisTemplate.keys(namespace + "*");
        assertThat(storedKeys).hasSize(2);
        var ttlBefore = storedKeys.stream()
                .collect(java.util.stream.Collectors.toMap(
                        key -> key,
                        key -> redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)
                ));

        DailyUsageService.Snapshot snapshot = service.get(
                new DailyUsageService.Requester("member-1", null, "203.0.113.10")
        );

        assertThat(snapshot.health()).isEqualTo(new DailyUsageService.Counter(5, 1, 4));
        assertThat(snapshot.headline()).isEqualTo(new DailyUsageService.Counter(10, 2, 8));
        assertThat(snapshot.resetsAt().atZone(KOREA_ZONE).toLocalDate())
                .isEqualTo(LocalDate.now(clock.withZone(KOREA_ZONE)).plusDays(1));
        assertThat(snapshot.resetsAt().atZone(KOREA_ZONE).toLocalTime())
                .isEqualTo(LocalTime.MIDNIGHT);
        storedKeys.forEach(key -> assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(ttlBefore.get(key)));

        DailyUsageService.Snapshot guest = service.get(
                new DailyUsageService.Requester(null, null, "203.0.113.20")
        );
        assertThat(guest.health().used()).isZero();
        assertThat(guest.headline().used()).isZero();
        assertThat(guest.guestBrowserCookie()).isNotNull();
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
