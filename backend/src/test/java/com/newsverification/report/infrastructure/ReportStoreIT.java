/* Native MySQL 문제 신고 저장 통합 검증 */
package com.newsverification.report.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.publisher.infrastructure.NativeMySqlTestConnectionGuard;
import com.newsverification.report.application.ReportException;
import com.newsverification.report.application.ReportStore;
import com.newsverification.report.domain.AnalysisReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL의 신고 Snapshot·상태 이력·만료 정리 검증 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class ReportStoreIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T01:00:00Z");

    @Autowired
    private ReportStore store;

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

    /** 신고 당시 Snapshot과 사용자 소유권 유지 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void savesImmutableSnapshotAndLimitsMemberLookup() {
        cleanupFixture();
        long reporterId = insertUser("reporter26", "reporter26@example.com", "USER");
        long anotherUserId = insertUser("another26", "another26@example.com", "USER");

        try {
            ReportStore.StoredReport saved = store.save(newReport(reporterId, CREATED_AT));

            assertThat(store.findByReporter(saved.id(), reporterId)).isPresent();
            assertThat(store.findByReporter(saved.id(), anotherUserId)).isEmpty();
            assertThat(saved.snapshot()).containsEntry("schemaVersion", 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report_status_history WHERE report_id = ?",
                    Integer.class,
                    saved.id()
            )).isEqualTo(1);
        } finally {
            cleanupFixture();
        }
    }

    /** 정확한 만료 경계의 완료 신고만 삭제 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void deletesOnlyExpiredResolvedReportWithHistory() {
        cleanupFixture();
        long reporterId = insertUser("reporter26", "reporter26@example.com", "USER");
        long administratorId = insertUser("admin26", "admin26@example.com", "ADMIN");

        try {
            ReportStore.StoredReport expiring = store.save(newReport(reporterId, CREATED_AT));
            ReportStore.StoredReport open = store.save(newReport(reporterId, CREATED_AT.plusSeconds(60)));
            ReportStore.StoredReport working = store.save(newReport(reporterId, CREATED_AT.plusSeconds(120)));
            ReportStore.StoredReport fresh = store.save(newReport(reporterId, CREATED_AT.plusSeconds(180)));
            Instant completedAt = Instant.parse("2026-08-02T01:00:00Z");
            ReportStore.StoredReport resolved = store.transition(new ReportStore.Transition(
                    expiring.id(), administratorId, AnalysisReport.Status.RESOLVED,
                    "신고 내용을 확인했습니다.", completedAt, completedAt, expiring.version()
            ));
            ReportStore.StoredReport inProgress = store.transition(new ReportStore.Transition(
                    working.id(), administratorId, AnalysisReport.Status.IN_PROGRESS,
                    null, null, completedAt, working.version()
            ));
            ReportStore.StoredReport freshResolved = store.transition(new ReportStore.Transition(
                    fresh.id(), administratorId, AnalysisReport.Status.RESOLVED,
                    "신고 내용을 확인했습니다.", completedAt.plusSeconds(1),
                    completedAt.plusSeconds(1), fresh.version()
            ));

            int deleted = store.deleteResolvedCompletedAtOrBefore(completedAt);

            assertThat(deleted).isEqualTo(1);
            assertThat(store.findById(resolved.id())).isEmpty();
            assertThat(store.findById(open.id())).isPresent();
            assertThat(store.findById(inProgress.id())).isPresent();
            assertThat(store.findById(freshResolved.id())).isPresent();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report_status_history WHERE report_id = ?",
                    Integer.class,
                    resolved.id()
            )).isZero();
        } finally {
            cleanupFixture();
        }
    }

    /** 오래된 Version의 관리자 상태 전환 차단 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsStaleAdministrativeTransition() {
        cleanupFixture();
        long reporterId = insertUser("reporter26", "reporter26@example.com", "USER");
        long administratorId = insertUser("admin26", "admin26@example.com", "ADMIN");

        try {
            ReportStore.StoredReport saved = store.save(newReport(reporterId, CREATED_AT));
            store.transition(new ReportStore.Transition(
                    saved.id(), administratorId, AnalysisReport.Status.IN_PROGRESS,
                    null, null, CREATED_AT.plusSeconds(60), saved.version()
            ));

            assertThatThrownBy(() -> store.transition(new ReportStore.Transition(
                    saved.id(), administratorId, AnalysisReport.Status.RESOLVED,
                    "신고 내용을 확인했습니다.", CREATED_AT.plusSeconds(120),
                    CREATED_AT.plusSeconds(120), saved.version()
            ))).isInstanceOf(ReportException.class).hasMessage("VERSION_CONFLICT");

            assertThat(store.findById(saved.id())).get()
                    .extracting(ReportStore.StoredReport::status)
                    .isEqualTo(AnalysisReport.Status.IN_PROGRESS);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM report_status_history WHERE report_id = ?",
                    Integer.class,
                    saved.id()
            )).isEqualTo(2);
        } finally {
            cleanupFixture();
        }
    }

    private ReportStore.NewReport newReport(long reporterId, Instant createdAt) {
        return new ReportStore.NewReport(
                reporterId,
                AnalysisReport.ReportType.WRONG_JUDGMENT,
                AnalysisReport.AnalysisType.HEALTH,
                "판정을 다시 확인해 주세요.",
                "https://news.example/article",
                "건강 기사 제목",
                Map.of(
                        "schemaVersion", 1,
                        "analysisType", "HEALTH",
                        "article", Map.of("publisher", "통합검증언론사")
                ),
                createdAt
        );
    }

    private long insertUser(String username, String email, String role) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    account_type, role, status, email, username, password_hash,
                    phone_number, email_verified_at, invite_code_verified_at
                ) VALUES ('LOCAL', ?, 'ACTIVE', ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """,
                role,
                email,
                username,
                "{bcrypt}$2a$10$integration-test-hash",
                "010" + String.format("%08d", Math.abs(username.hashCode() % 100000000))
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?",
                Long.class,
                username
        );
    }

    private void cleanupFixture() {
        jdbcTemplate.update("DELETE FROM users WHERE username IN ('reporter26', 'another26', 'admin26')");
    }
}
