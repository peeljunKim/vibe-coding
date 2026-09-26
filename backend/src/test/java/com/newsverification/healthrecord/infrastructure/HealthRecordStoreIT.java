/* Native MySQL 저장 건강 분석 통합 검증 */
package com.newsverification.healthrecord.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.healthrecord.application.HealthRecordStore;
import com.newsverification.publisher.infrastructure.NativeMySqlTestConnectionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL의 결과·주장·근거 저장과 목록 조회 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class HealthRecordStoreIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();
    private static final Instant ANALYZED_AT = Instant.parse("2026-09-24T01:00:00Z");

    @Autowired
    private HealthRecordStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 테스트 전용 DataSource 설정 */
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_CONNECTION::databaseUrl);
        registry.add("spring.datasource.username", TEST_CONNECTION::username);
        registry.add("spring.datasource.password", TEST_CONNECTION::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** 같은 완료 결과의 1회 저장과 최신순 목록 조회 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void savesCompleteResultOnceAndListsIt() {
        cleanupFixture();
        long userId = insertUser();
        insertPublisherDomain();
        HealthRecordStore.SaveCommand command = new HealthRecordStore.SaveCommand(
                userId,
                result(),
                ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60)
        );

        try {
            HealthRecordStore.SavedRecord first = store.save(command);
            HealthRecordStore.SavedRecord duplicate = store.save(command);
            HealthRecordStore.PageResult page = store.findAll(userId, ANALYZED_AT, 0, 20);

            assertThat(duplicate.id()).isEqualTo(first.id());
            assertThat(page.items()).extracting(HealthRecordStore.SavedRecord::id)
                    .containsExactly(first.id());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM health_claims WHERE health_analysis_record_id = ?",
                    Integer.class,
                    first.id()
            )).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM health_evidences WHERE health_analysis_record_id = ?",
                    Integer.class,
                    first.id()
            )).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM health_claim_evidences relation
                    JOIN health_claims claim ON claim.id = relation.health_claim_id
                    WHERE claim.health_analysis_record_id = ?
                    """,
                    Integer.class,
                    first.id()
            )).isEqualTo(1);
        } finally {
            cleanupFixture();
        }
    }

    /** 같은 완료 결과의 동시 저장 중복 방지 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void preventsConcurrentDuplicateSaves() throws Exception {
        cleanupFixture();
        long userId = insertUser();
        insertPublisherDomain();
        HealthRecordStore.SaveCommand command = new HealthRecordStore.SaveCommand(
                userId,
                result(),
                ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60)
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var saves = List.of(
                    executor.submit(() -> saveAfterSignal(command, ready, start)),
                    executor.submit(() -> saveAfterSignal(command, ready, start))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long firstId = saves.get(0).get(10, TimeUnit.SECONDS).id();
            long secondId = saves.get(1).get(10, TimeUnit.SECONDS).id();

            assertThat(secondId).isEqualTo(firstId);
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM health_analysis_records
                    WHERE user_id = ? AND analyzed_at = ?
                    """,
                    Integer.class,
                    userId,
                    ANALYZED_AT
            )).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            cleanupFixture();
        }
    }

    private HealthRecordStore.SavedRecord saveAfterSignal(
            HealthRecordStore.SaveCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent save start timed out");
        }
        return store.save(command);
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM users WHERE username = 'record26'");
        jdbcTemplate.update("DELETE FROM news_publisher_domains WHERE hostname = 'news.example'");
        jdbcTemplate.update("DELETE FROM news_publishers WHERE name = '통합검증언론사'");
    }

    private long insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    account_type, role, status, email, username, password_hash,
                    phone_number, email_verified_at, invite_code_verified_at
                ) VALUES ('LOCAL', 'USER', 'ACTIVE', ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                "record26@example.com",
                "record26",
                "{bcrypt}$2a$10$integration-test-hash",
                "01098765432"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = 'record26'",
                Long.class
        );
    }

    private void insertPublisherDomain() {
        jdbcTemplate.update("""
                INSERT INTO news_publishers (name, category, status)
                VALUES ('통합검증언론사', 'HEALTH_MEDICAL', 'ACTIVE')
                """);
        Long publisherId = jdbcTemplate.queryForObject(
                "SELECT id FROM news_publishers WHERE name = '통합검증언론사'",
                Long.class
        );
        jdbcTemplate.update("""
                INSERT INTO news_publisher_domains (publisher_id, hostname, status)
                VALUES (?, 'news.example', 'ACTIVE')
                """, publisherId);
    }

    private HealthAnalysisResult result() {
        HealthAnalysisResult.Evidence evidence = new HealthAnalysisResult.Evidence(
                HealthAnalysisResult.EvidenceSourceKind.OFFICIAL,
                "GUIDE-2026-1",
                HealthAnalysisResult.EvidenceStudyType.GUIDELINE,
                HealthAnalysisResult.EvidenceRelationType.CONTEXT,
                "건강 정보 안내",
                "공식기관",
                LocalDate.parse("2026-09-20"),
                URI.create("https://evidence.example/guide"),
                "현재 근거만으로 단정하기 어렵습니다.",
                null
        );
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"),
                        "건강 기사 제목",
                        "통합검증언론사",
                        OffsetDateTime.parse("2026-09-24T09:00:00+09:00"),
                        null
                ),
                ANALYZED_AT,
                HealthAnalysisResult.OverallStatus.CAUTION,
                new BigDecimal("0.00"),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        "건강 기사 핵심 주장",
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "근거가 부족합니다.",
                        List.of(evidence)
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                true
        );
    }
}
