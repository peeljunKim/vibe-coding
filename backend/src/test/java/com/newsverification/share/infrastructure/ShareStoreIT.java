/* Native MySQL 공유 저장 통합 검증 */
package com.newsverification.share.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.publisher.infrastructure.NativeMySqlTestConnectionGuard;
import com.newsverification.share.application.ShareService;
import com.newsverification.share.application.ShareStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL의 Digest·소유권·Cascade·만료 경계 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class ShareStoreIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();
    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private ShareStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_CONNECTION::databaseUrl);
        registry.add("spring.datasource.username", TEST_CONNECTION::username);
        registry.add("spring.datasource.password", TEST_CONNECTION::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sharesOwnedHealthSnapshotAndRevokesWithoutRawToken() {
        cleanupFixture();
        long userId = insertUser("share26");
        long otherUserId = insertUser("share27");
        long domainId = insertPublisherDomain();
        long recordId = insertHealthRecord(userId, domainId);
        byte[] digest = digest("health-share-it-token");

        try {
            ShareStore.HealthSource source = store.findHealthSource(recordId, userId, NOW).orElseThrow();
            store.saveHealth(recordId, digest, "CAUTION", NOW, NOW.plusSeconds(604_800));

            assertThat(source.claims()).hasSize(1);
            assertThat(source.article().publishedAt())
                    .isEqualTo(NOW.minusSeconds(3_600).atOffset(ZoneOffset.UTC));
            assertThat(source.article().modifiedAt())
                    .isEqualTo(NOW.minusSeconds(1_800).atOffset(ZoneOffset.UTC));
            assertThat(store.findHealth(digest, NOW)).get()
                    .extracting(ShareService.HealthResult::overallStatus)
                    .isEqualTo("CAUTION");
            assertThat(store.revokeHealth(digest, otherUserId, NOW.plusSeconds(1))).isFalse();
            assertThat(store.revokeHealth(digest, userId, NOW.plusSeconds(1))).isTrue();
            assertThat(store.findHealth(digest, NOW.plusSeconds(2))).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM health_share_links WHERE token_digest = ?",
                    Integer.class,
                    digest
            )).isEqualTo(1);
        } finally {
            cleanupFixture();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void storesMinimumHeadlineSnapshotAndDeletesOnRevokeOrExpiry() {
        cleanupFixture();
        long userId = insertUser("share26");
        insertPublisherDomain();
        byte[] digest = digest("headline-share-it-token");

        try {
            store.saveHeadline(
                    userId,
                    new ShareService.Article(
                            "https://share.example/article", "기존 기사 제목", "공유통합언론사", null, null
                    ),
                    digest,
                    "중립적인 대체 제목",
                    "EXAGGERATED",
                    NOW,
                    NOW,
                    NOW.plusSeconds(604_800),
                    "mock-headline-analysis-v1",
                    "headline-analysis-policy-v1",
                    List.of(new ShareService.HeadlineIssue("EXAGGERATED", "표현을 과장했습니다."))
            );

            assertThat(store.findHeadline(digest, NOW)).get()
                    .extracting(ShareService.HeadlineResult::alternativeHeadline)
                    .isEqualTo("중립적인 대체 제목");
            assertThat(store.deleteHeadline(digest, userId)).isTrue();
            assertThat(store.findHeadline(digest, NOW)).isEmpty();

            byte[] expiredDigest = digest("expired-headline-share-token");
            store.saveHeadline(
                    userId,
                    new ShareService.Article(
                            "https://share.example/expired", "만료 기사 제목", "공유통합언론사", null, null
                    ),
                    expiredDigest, null, "NO_ISSUE", NOW, NOW, NOW.plusSeconds(1),
                    "mock-headline-analysis-v1", "headline-analysis-policy-v1",
                    List.of(new ShareService.HeadlineIssue("NO_ISSUE", "문제 없음"))
            );
            assertThat(store.deleteExpiredHeadline(NOW.plusSeconds(1))).isEqualTo(1);
            assertThat(store.findHeadline(expiredDigest, NOW)).isEmpty();
        } finally {
            cleanupFixture();
        }
    }

    private long insertUser(String username) {
        jdbc.update("""
                INSERT INTO users (
                    account_type, role, status, email, username, password_hash,
                    phone_number, email_verified_at, invite_code_verified_at
                ) VALUES ('LOCAL', 'USER', 'ACTIVE', ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                username + "@example.com", username, "{bcrypt}$2a$10$integration-test-hash",
                "010" + (username.endsWith("6") ? "12345678" : "12345679")
        );
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private long insertPublisherDomain() {
        jdbc.update("""
                INSERT INTO news_publishers (name, category, status)
                VALUES ('공유통합언론사', 'HEALTH_MEDICAL', 'ACTIVE')
                """);
        long publisherId = jdbc.queryForObject(
                "SELECT id FROM news_publishers WHERE name = '공유통합언론사'", Long.class
        );
        jdbc.update("""
                INSERT INTO news_publisher_domains (publisher_id, hostname, status)
                VALUES (?, 'share.example', 'ACTIVE')
                """, publisherId);
        return jdbc.queryForObject(
                "SELECT id FROM news_publisher_domains WHERE hostname = 'share.example'", Long.class
        );
    }

    private long insertHealthRecord(long userId, long domainId) {
        jdbc.update("""
                INSERT INTO health_analysis_records (
                    user_id, publisher_domain_id, article_url, normalized_url_digest, article_title,
                    article_published_at, article_modified_at, analyzed_at, expires_at,
                    overall_status, total_claim_count, supported_claim_count,
                    verification_rate, expert_review_status, ai_model_version, policy_version,
                    evidence_allowlist_version
                ) VALUES (?, ?, 'https://share.example/article', ?, '공유 건강 기사', ?, ?, ?, ?, 'CAUTION',
                          1, 0, 0.00, 'NOT_REVIEWED', 'mock-health-analysis-v1',
                          'health-analysis-policy-v1', 'evidence-allowlist-v1')
                """,
                userId, domainId, digest("https://share.example/article"),
                NOW.minusSeconds(3_600), NOW.minusSeconds(1_800), NOW, NOW.plusSeconds(2_592_000)
        );
        long recordId = jdbc.queryForObject(
                "SELECT id FROM health_analysis_records WHERE user_id = ?", Long.class, userId
        );
        jdbc.update("""
                INSERT INTO health_claims (
                    health_analysis_record_id, claim_order, claim_text, status, easy_reason
                ) VALUES (?, 1, '건강 기사 핵심 주장', 'INSUFFICIENT', '근거가 부족합니다.')
                """, recordId);
        return recordId;
    }

    private void cleanupFixture() {
        jdbc.update("DELETE FROM users WHERE username IN ('share26', 'share27')");
        jdbc.update("DELETE FROM news_publisher_domains WHERE hostname = 'share.example'");
        jdbc.update("DELETE FROM news_publishers WHERE name = '공유통합언론사'");
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
