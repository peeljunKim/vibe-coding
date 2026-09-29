/* Redis 분석 공용 Cache 통합 검증 */
package com.newsverification.analysiscache.infrastructure;

import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.AnalysisCacheKeyFactory;
import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 기능 격리·TTL 지터·비민감 저장 경계 검증 */
@EnabledIfEnvironmentVariable(named = "REDIS_IT_ENABLED", matches = "true")
class RedisAnalysisResultCacheIT {

    private static final Duration BASE_TTL = Duration.ofDays(3);
    private static final AnalysisCacheVersions VERSIONS = AnalysisCacheVersions.mockDefaults();

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAnalysisResultCache cache;
    private final Set<String> createdKeys = new HashSet<>();

    /** 테스트 전용 Redis와 실행별 Namespace 구성 */
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
        cache = new RedisAnalysisResultCache(
                redisTemplate,
                new ObjectMapper(),
                requiredEnvironment("REDIS_TEST_NAMESPACE"),
                BASE_TTL,
                Clock.systemUTC()
        );
    }

    /** 실행별 Cache Key만 정리 */
    @AfterEach
    void tearDown() {
        if (redisTemplate != null && cache != null) {
            redisTemplate.delete(createdKeys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    /** 건강·제목 결과 격리와 원문·사용자 정보 미저장 */
    @Test
    void separatesFeatureCachesWithoutArticleBodyOrUserIdentity() {
        String articleUrl = "https://news.example/article";
        AnalysisCacheKey healthKey = AnalysisCacheKeyFactory.health(articleUrl, VERSIONS)
                .orElseThrow();
        AnalysisCacheKey headlineKey = AnalysisCacheKeyFactory.headline(articleUrl, VERSIONS)
                .orElseThrow();
        HealthAnalysisResult healthResult = healthResult(articleUrl);
        HeadlineAnalysisResult headlineResult = headlineResult(articleUrl);
        String viewerFingerprint = "a".repeat(64);

        cache.saveHealth(healthKey, healthResult, viewerFingerprint);
        cache.saveHeadline(headlineKey, headlineResult, viewerFingerprint);
        track(healthKey, viewerFingerprint);
        track(headlineKey, viewerFingerprint);

        assertThat(cache.findHealth(healthKey).orElseThrow().result()).isEqualTo(healthResult);
        assertThat(cache.findHeadline(headlineKey).orElseThrow().result()).isEqualTo(headlineResult);
        assertThat(cache.findHeadline(healthKey)).isEmpty();
        assertThat(cache.findHealth(headlineKey)).isEmpty();

        String healthStored = redisTemplate.opsForHash()
                .entries(cache.cacheKey(healthKey)).toString();
        String healthViewerKey = cache.viewerKey(healthKey, viewerFingerprint);
        assertThat(healthStored)
                .contains("mock-health-analysis-v1")
                .doesNotContain("기사 원문 비밀 내용")
                .doesNotContain("member-42");
        assertThat(healthViewerKey).doesNotContain("member-42");
        assertThat(redisTemplate.hasKey(healthViewerKey)).isTrue();
    }

    /** 72시간 이하 결정적 지터와 조회 TTL 무연장 */
    @Test
    void appliesThreeDayTtlWithBoundedJitterWithoutReadExtension() throws Exception {
        AnalysisCacheKey key = AnalysisCacheKeyFactory.health(
                "https://news.example/ttl",
                VERSIONS
        ).orElseThrow();
        cache.saveHealth(key, healthResult("https://news.example/ttl"), "b".repeat(64));
        track(key, "b".repeat(64));
        Long ttlBeforeRead = redisTemplate.getExpire(cache.cacheKey(key), TimeUnit.MILLISECONDS);

        Thread.sleep(25);
        cache.findHealth(key);
        Long ttlAfterRead = redisTemplate.getExpire(cache.cacheKey(key), TimeUnit.MILLISECONDS);

        assertThat(ttlBeforeRead).isNotNull().isPositive();
        assertThat(ttlAfterRead).isNotNull().isPositive().isLessThan(ttlBeforeRead);
        assertThat(ttlBeforeRead)
                .isLessThanOrEqualTo(BASE_TTL.toMillis())
                .isGreaterThanOrEqualTo(BASE_TTL.minusMinutes(30).toMillis() - 1_000L);
    }

    /** 테스트에서 생성한 Cache·Viewer Key 등록 */
    private void track(AnalysisCacheKey key, String viewerFingerprint) {
        createdKeys.add(cache.cacheKey(key));
        createdKeys.add(cache.viewerKey(key, viewerFingerprint));
    }

    /** 건강 결과 Fixture */
    private HealthAnalysisResult healthResult(String articleUrl) {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create(articleUrl),
                        "건강 기사",
                        "테스트언론사",
                        OffsetDateTime.parse("2026-09-29T09:00:00+09:00"),
                        null
                ),
                Instant.parse("2026-09-29T00:00:00Z"),
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        "검증할 주장",
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "자료 부족",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                VERSIONS.healthModelVersion(),
                VERSIONS.healthPolicyVersion(),
                VERSIONS.healthEvidenceAllowlistVersion(),
                true
        );
    }

    /** 제목 결과 Fixture */
    private HeadlineAnalysisResult headlineResult(String articleUrl) {
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        URI.create(articleUrl),
                        "기사 제목",
                        "테스트언론사",
                        OffsetDateTime.parse("2026-09-29T09:00:00+09:00"),
                        null
                ),
                Instant.parse("2026-09-29T00:00:00Z"),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE,
                        "문제 없음"
                )),
                null,
                VERSIONS.headlineModelVersion(),
                VERSIONS.headlinePolicyVersion()
        );
    }

    /** 필수 테스트 환경 변수 조회 */
    private String requiredEnvironment(String name) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException(name + " is required"));
    }
}
