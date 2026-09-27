/* 문제 신고 Application 동작 검증 */
package com.newsverification.report.application;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import com.newsverification.report.domain.AnalysisReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 신고 접수·조회·처리·만료 정리의 공개 Use Case 검증 */
class DefaultReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T03:00:00Z");

    private final CapturingStore store = new CapturingStore();
    private final CapturingMailPort mailPort = new CapturingMailPort();
    private final ReportService service = new DefaultReportService(
            new FixedHealthJobs(),
            new FixedHeadlineJobs(),
            store,
            mailPort,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void createsHealthReportFromOwnedCompletedAnalysisSnapshot() {
        ReportService.Detail created = service.create("42", new ReportService.CreateCommand(
                "HEALTH", "health-1", "IRRELEVANT_EVIDENCE", " 근거를 다시 확인해 주세요. "
        ));

        assertThat(created.id()).isEqualTo(91L);
        assertThat(store.saved.reporterUserId()).isEqualTo(42L);
        assertThat(store.saved.description()).isEqualTo("근거를 다시 확인해 주세요.");
        assertThat(store.saved.articleUrl()).isEqualTo("https://news.example/health");
        assertThat(store.saved.snapshot())
                .containsEntry("schemaVersion", 1)
                .containsEntry("analysisType", "HEALTH")
                .doesNotContainKeys("articleBody", "email", "token", "usage");
        assertThat(mailPort.newReportIds).containsExactly(91L);
    }

    @Test
    void createsHeadlineReportWithoutHealthTopicRestriction() {
        ReportService.Detail created = service.create("42", new ReportService.CreateCommand(
                "HEADLINE", "headline-1", "INACCURATE_HEADLINE", "제목 판정을 확인해 주세요."
        ));

        assertThat(created.analysisType()).isEqualTo(AnalysisReport.AnalysisType.HEADLINE);
        assertThat(store.saved.snapshot()).containsEntry("analysisType", "HEADLINE");
    }

    @Test
    void rejectsMissingOrIncompleteOwnedAnalysisWithoutLeakingOwnership() {
        assertThatThrownBy(() -> service.create("99", new ReportService.CreateCommand(
                "HEALTH", "health-1", "WRONG_JUDGMENT", "판정을 확인해 주세요."
        ))).isInstanceOf(ReportException.class).hasMessage("ANALYSIS_NOT_FOUND");

        assertThatThrownBy(() -> service.create("42", new ReportService.CreateCommand(
                "HEALTH", "running", "WRONG_JUDGMENT", "판정을 확인해 주세요."
        ))).isInstanceOf(ReportException.class).hasMessage("ANALYSIS_NOT_COMPLETED");
    }

    @Test
    void validatesUnicodeCodePointLengthAfterTrimming() {
        assertThatThrownBy(() -> service.create("42", new ReportService.CreateCommand(
                "HEALTH", "health-1", "WRONG_JUDGMENT", "😀".repeat(2001)
        ))).isInstanceOf(ReportException.class).hasMessage("INVALID_DESCRIPTION");
    }

    @Test
    void onlyReturnsReportsOwnedByRequester() {
        store.savedReport = report(92L, 42L, AnalysisReport.Status.OPEN, null, 0L);

        assertThat(service.findOne("42", 92L).id()).isEqualTo(92L);
        assertThatThrownBy(() -> service.findOne("41", 92L))
                .isInstanceOf(ReportException.class)
                .hasMessage("REPORT_NOT_FOUND");
    }

    @Test
    void resolvesOnceAndSendsOneCompletionMail() {
        store.savedReport = report(93L, 42L, AnalysisReport.Status.IN_PROGRESS, null, 1L);

        ReportService.Detail resolved = service.updateAsAdmin("7", 93L, new ReportService.UpdateCommand(
                "RESOLVED", "신고 내용을 확인했습니다.", 1L
        ));

        assertThat(resolved.status()).isEqualTo(AnalysisReport.Status.RESOLVED);
        assertThat(resolved.completedAt()).isEqualTo(NOW);
        assertThat(mailPort.resolvedRecipients).containsExactly("member@example.com");

        assertThatThrownBy(() -> service.updateAsAdmin("7", 93L, new ReportService.UpdateCommand(
                "IN_PROGRESS", "다시 확인합니다.", 2L
        ))).isInstanceOf(ReportException.class).hasMessage("REPORT_STATE_CONFLICT");
        assertThat(mailPort.resolvedRecipients).hasSize(1);
    }

    @Test
    void deletesOnlyReportsCompletedAtLeastThirtyDaysAgo() {
        assertThat(service.cleanupExpiredReports()).isEqualTo(2);
        assertThat(store.deletedCutoff).isEqualTo(NOW.minusSeconds(30L * 24 * 60 * 60));
    }

    private static ReportStore.StoredReport report(long id, long reporterId, AnalysisReport.Status status,
                                                    Instant completedAt, long version) {
        return new ReportStore.StoredReport(
                id, reporterId, "member@example.com", AnalysisReport.ReportType.WRONG_JUDGMENT,
                AnalysisReport.AnalysisType.HEALTH, "설명", "https://news.example/health", "건강 기사",
                Map.of("schemaVersion", 1, "article", Map.of("publisher", "테스트언론사")),
                status, status == AnalysisReport.Status.RESOLVED ? "신고 내용을 확인했습니다." : null,
                completedAt, NOW.minusSeconds(60), NOW, version
        );
    }

    private static HealthAnalysisResult healthResult() {
        var evidence = new HealthAnalysisResult.Evidence(
                HealthAnalysisResult.EvidenceSourceKind.OFFICIAL, "guide-1",
                HealthAnalysisResult.EvidenceStudyType.GUIDELINE,
                HealthAnalysisResult.EvidenceRelationType.CONTEXT,
                "공식 안내", "공식기관", LocalDate.parse("2026-09-20"),
                URI.create("https://evidence.example/guide"), "근거 요약", null
        );
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/health"), "건강 기사", "테스트언론사",
                        OffsetDateTime.parse("2026-09-25T10:00:00+09:00"), null
                ),
                NOW.minusSeconds(120), HealthAnalysisResult.OverallStatus.CAUTION,
                new BigDecimal("50.00"), 1, 2,
                List.of(new HealthAnalysisResult.Claim(
                        1, "핵심 주장", HealthAnalysisResult.ClaimStatus.NEEDS_REVIEW,
                        "추가 확인 필요", List.of(evidence)
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-v1", "policy-v1", "allowlist-v1", false
        );
    }

    private static HeadlineAnalysisResult headlineResult() {
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/headline"), "기사 제목", "테스트언론사",
                        OffsetDateTime.parse("2026-09-25T10:00:00+09:00"), null
                ),
                NOW.minusSeconds(120),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.EXAGGERATED, "과장 표현"
                )),
                "중립 제목"
        );
    }

    private static final class FixedHealthJobs implements HealthAnalysisJobService {
        @Override
        public Acceptance accept(String articleUrl, Requester requester) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Progress> find(String analysisId, Requester requester) {
            if (!"42".equals(requester.memberId())) {
                return Optional.empty();
            }
            boolean completed = !"running".equals(analysisId);
            return Optional.of(new Progress(
                    analysisId, completed ? AnalysisJobStatus.COMPLETED : AnalysisJobStatus.PROCESSING,
                    completed ? AnalysisJobStage.COMPLETED : AnalysisJobStage.GENERATING_RESULT,
                    NOW.plusSeconds(90), NOW.plusSeconds(1800), completed ? healthResult() : null,
                    null, null
            ));
        }
    }

    private static final class FixedHeadlineJobs implements HeadlineAnalysisJobService {
        @Override
        public Acceptance accept(String articleUrl, Requester requester) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Progress> find(String analysisId, Requester requester) {
            if (!"42".equals(requester.memberId())) {
                return Optional.empty();
            }
            return Optional.of(new Progress(
                    analysisId, AnalysisJobStatus.COMPLETED, AnalysisJobStage.COMPLETED,
                    NOW.plusSeconds(90), NOW.plusSeconds(1800), headlineResult(), null, null
            ));
        }
    }

    private static final class CapturingStore implements ReportStore {
        private NewReport saved;
        private StoredReport savedReport;
        private Instant deletedCutoff;

        @Override
        public StoredReport save(NewReport report) {
            saved = report;
            savedReport = new StoredReport(
                    91L, report.reporterUserId(), "member@example.com", report.reportType(),
                    report.analysisType(), report.description(), report.articleUrl(), report.articleTitle(),
                    report.snapshot(), AnalysisReport.Status.OPEN, null, null,
                    report.createdAt(), report.createdAt(), 0L
            );
            return savedReport;
        }

        @Override
        public PageResult findAllByReporter(long reporterUserId, int page, int size) {
            return page(savedReport);
        }

        @Override
        public Optional<StoredReport> findByReporter(long reportId, long reporterUserId) {
            return savedReport != null && savedReport.id() == reportId && savedReport.reporterUserId() == reporterUserId
                    ? Optional.of(savedReport) : Optional.empty();
        }

        @Override
        public PageResult findAll(int page, int size) {
            return page(savedReport);
        }

        @Override
        public Optional<StoredReport> findById(long reportId) {
            return savedReport != null && savedReport.id() == reportId ? Optional.of(savedReport) : Optional.empty();
        }

        @Override
        public StoredReport transition(Transition command) {
            if (savedReport.version() != command.expectedVersion()) {
                throw new ReportException("VERSION_CONFLICT");
            }
            savedReport = new StoredReport(
                    savedReport.id(), savedReport.reporterUserId(), savedReport.reporterEmail(),
                    savedReport.reportType(), savedReport.analysisType(), savedReport.description(),
                    savedReport.articleUrl(), savedReport.articleTitle(), savedReport.snapshot(), command.status(),
                    command.adminReply(), command.completedAt(), savedReport.createdAt(), command.changedAt(),
                    savedReport.version() + 1
            );
            return savedReport;
        }

        @Override
        public int deleteResolvedCompletedAtOrBefore(Instant cutoff) {
            deletedCutoff = cutoff;
            return 2;
        }

        private PageResult page(StoredReport report) {
            List<StoredReport> items = report == null ? List.of() : List.of(report);
            return new PageResult(items, 0, 20, items.size(), items.isEmpty() ? 0 : 1, false);
        }
    }

    private static final class CapturingMailPort implements ReportMailPort {
        private final List<Long> newReportIds = new ArrayList<>();
        private final List<String> resolvedRecipients = new ArrayList<>();

        @Override
        public void notifyNewReport(long reportId) {
            newReportIds.add(reportId);
        }

        @Override
        public void notifyResolved(String recipientEmail, long reportId) {
            resolvedRecipients.add(recipientEmail);
        }
    }
}
