/* 문제 신고 Use Case 구현 */
package com.newsverification.report.application;

import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import com.newsverification.report.domain.AnalysisReport;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 소유한 완료 분석의 불변 Snapshot 신고 처리 */
@Service
public class DefaultReportService implements ReportService {

    private static final Duration COMPLETED_RETENTION = Duration.ofDays(30);

    private final HealthAnalysisJobService healthJobs;
    private final HeadlineAnalysisJobService headlineJobs;
    private final ReportStore store;
    private final ReportMailPort mailPort;
    private final Clock clock;

    public DefaultReportService(
            HealthAnalysisJobService healthJobs,
            HeadlineAnalysisJobService headlineJobs,
            ReportStore store,
            ReportMailPort mailPort,
            Clock clock
    ) {
        this.healthJobs = healthJobs;
        this.headlineJobs = headlineJobs;
        this.store = store;
        this.mailPort = mailPort;
        this.clock = clock;
    }

    @Override
    public Detail create(String memberId, CreateCommand command) {
        long reporterUserId = userId(memberId);
        AnalysisReport.AnalysisType analysisType = enumValue(
                AnalysisReport.AnalysisType.class, command.analysisType(), "INVALID_ANALYSIS_TYPE"
        );
        AnalysisReport.ReportType reportType = enumValue(
                AnalysisReport.ReportType.class, command.reportType(), "INVALID_REPORT_TYPE"
        );
        String description;
        try {
            description = AnalysisReport.validateDescription(command.description());
        } catch (IllegalArgumentException exception) {
            throw new ReportException("INVALID_DESCRIPTION");
        }
        if (command.analysisId() == null || command.analysisId().isBlank()) {
            throw new ReportException("INVALID_ANALYSIS_ID");
        }

        Snapshot snapshot = analysisType == AnalysisReport.AnalysisType.HEALTH
                ? healthSnapshot(memberId, command.analysisId())
                : headlineSnapshot(memberId, command.analysisId());
        ReportStore.StoredReport saved = store.save(new ReportStore.NewReport(
                reporterUserId,
                reportType,
                analysisType,
                description,
                snapshot.articleUrl(),
                snapshot.articleTitle(),
                snapshot.value(),
                clock.instant()
        ));
        mailPort.notifyNewReport(saved.id());
        return detail(saved);
    }

    @Override
    public PageResult findMine(String memberId, int page, int size) {
        validatePagination(page, size);
        return page(store.findAllByReporter(userId(memberId), page, size));
    }

    @Override
    public Detail findOne(String memberId, long reportId) {
        return store.findByReporter(reportId, userId(memberId))
                .map(DefaultReportService::detail)
                .orElseThrow(() -> new ReportException("REPORT_NOT_FOUND"));
    }

    @Override
    public PageResult findAllAsAdmin(int page, int size) {
        validatePagination(page, size);
        return page(store.findAll(page, size));
    }

    @Override
    public Detail findOneAsAdmin(long reportId) {
        return store.findById(reportId)
                .map(DefaultReportService::detail)
                .orElseThrow(() -> new ReportException("REPORT_NOT_FOUND"));
    }

    @Override
    public Detail updateAsAdmin(String administratorId, long reportId, UpdateCommand command) {
        long administratorUserId = userId(administratorId);
        AnalysisReport.Status target = enumValue(
                AnalysisReport.Status.class, command.status(), "INVALID_STATUS"
        );
        ReportStore.StoredReport current = store.findById(reportId)
                .orElseThrow(() -> new ReportException("REPORT_NOT_FOUND"));
        String reply = normalizeReply(target, command.adminReply());
        try {
            AnalysisReport.validateTransition(current.status(), target, reply);
        } catch (IllegalArgumentException exception) {
            throw new ReportException("REPORT_STATE_CONFLICT");
        }
        Instant now = clock.instant();
        ReportStore.StoredReport updated = store.transition(new ReportStore.Transition(
                reportId,
                administratorUserId,
                target,
                reply,
                target == AnalysisReport.Status.RESOLVED ? now : null,
                now,
                command.version()
        ));
        if (updated.status() == AnalysisReport.Status.RESOLVED) {
            mailPort.notifyResolved(updated.reporterEmail(), updated.id());
        }
        return detail(updated);
    }

    @Override
    public int cleanupExpiredReports() {
        return store.deleteResolvedCompletedAtOrBefore(clock.instant().minus(COMPLETED_RETENTION));
    }

    private Snapshot healthSnapshot(String memberId, String analysisId) {
        HealthAnalysisJobService.Progress progress = healthJobs.find(
                        analysisId,
                        new HealthAnalysisJobService.Requester(memberId, null, null, null)
                )
                .orElseThrow(() -> new ReportException("ANALYSIS_NOT_FOUND"));
        HealthAnalysisResult result = progress.result();
        if (progress.status() != AnalysisJobStatus.COMPLETED || result == null) {
            throw new ReportException("ANALYSIS_NOT_COMPLETED");
        }
        Map<String, Object> snapshot = baseSnapshot("HEALTH", result.analyzedAt(), result.article());
        Map<String, Object> resultValue = new LinkedHashMap<>();
        resultValue.put("overallStatus", result.overallStatus().name());
        resultValue.put("confirmationRate", result.confirmationRate());
        resultValue.put("confirmedClaimCount", result.confirmedClaimCount());
        resultValue.put("totalClaimCount", result.totalClaimCount());
        resultValue.put("expertReviewStatus", result.expertReviewStatus().name());
        resultValue.put("limitedEvidence", result.limitedEvidence());
        resultValue.put("claims", result.claims().stream().map(DefaultReportService::claim).toList());
        snapshot.put("result", resultValue);
        return new Snapshot(
                result.article().url().toASCIIString(), result.article().title(), snapshot
        );
    }

    private Snapshot headlineSnapshot(String memberId, String analysisId) {
        HeadlineAnalysisJobService.Progress progress = headlineJobs.find(
                        analysisId,
                        new HeadlineAnalysisJobService.Requester(memberId, null, null, null)
                )
                .orElseThrow(() -> new ReportException("ANALYSIS_NOT_FOUND"));
        HeadlineAnalysisResult result = progress.result();
        if (progress.status() != AnalysisJobStatus.COMPLETED || result == null) {
            throw new ReportException("ANALYSIS_NOT_COMPLETED");
        }
        Map<String, Object> snapshot = baseSnapshot("HEADLINE", result.analyzedAt(), result.article());
        Map<String, Object> resultValue = new LinkedHashMap<>();
        resultValue.put("issues", result.issues().stream().map(issue -> Map.of(
                "type", issue.type().name(),
                "explanation", issue.explanation()
        )).toList());
        if (result.alternativeHeadline() != null) {
            resultValue.put("alternativeHeadline", result.alternativeHeadline());
        }
        snapshot.put("result", resultValue);
        return new Snapshot(
                result.article().url().toASCIIString(), result.article().title(), snapshot
        );
    }

    private static Map<String, Object> baseSnapshot(
            String analysisType,
            Instant analyzedAt,
            HealthAnalysisResult.ArticleSummary article
    ) {
        return baseSnapshot(
                analysisType, analyzedAt, article.url().toASCIIString(), article.title(), article.publisher(),
                article.publishedAt() == null ? null : article.publishedAt().toString(),
                article.modifiedAt() == null ? null : article.modifiedAt().toString()
        );
    }

    private static Map<String, Object> baseSnapshot(
            String analysisType,
            Instant analyzedAt,
            HeadlineAnalysisResult.ArticleSummary article
    ) {
        return baseSnapshot(
                analysisType, analyzedAt, article.url().toASCIIString(), article.title(), article.publisher(),
                article.publishedAt() == null ? null : article.publishedAt().toString(),
                article.modifiedAt() == null ? null : article.modifiedAt().toString()
        );
    }

    private static Map<String, Object> baseSnapshot(
            String analysisType,
            Instant analyzedAt,
            String url,
            String title,
            String publisher,
            String publishedAt,
            String modifiedAt
    ) {
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("url", url);
        article.put("title", title);
        article.put("publisher", publisher);
        putIfPresent(article, "publishedAt", publishedAt);
        putIfPresent(article, "modifiedAt", modifiedAt);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("analysisType", analysisType);
        snapshot.put("analyzedAt", analyzedAt.toString());
        snapshot.put("article", article);
        return snapshot;
    }

    private static Map<String, Object> claim(HealthAnalysisResult.Claim claim) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("order", claim.order());
        value.put("claim", claim.claim());
        value.put("status", claim.status().name());
        value.put("reason", claim.reason());
        value.put("evidences", claim.evidences().stream().map(DefaultReportService::evidence).toList());
        return value;
    }

    private static Map<String, Object> evidence(HealthAnalysisResult.Evidence evidence) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("sourceKind", evidence.sourceKind().name());
        value.put("title", evidence.title());
        value.put("provider", evidence.provider());
        putIfPresent(value, "publishedOrUpdatedDate", evidence.publishedOrUpdatedDate());
        value.put("sourceUrl", evidence.sourceUrl().toASCIIString());
        value.put("summary", evidence.summary());
        return value;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value.toString());
        }
    }

    private static String normalizeReply(AnalysisReport.Status target, String reply) {
        if (reply == null && target != AnalysisReport.Status.RESOLVED) {
            return null;
        }
        try {
            return AnalysisReport.validateAdminReply(reply);
        } catch (IllegalArgumentException exception) {
            throw new ReportException("INVALID_ADMIN_REPLY");
        }
    }

    private static void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ReportException("INVALID_PAGINATION");
        }
    }

    private static long userId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ReportException("REPORT_ACCESS_DENIED");
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String code) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ReportException(code);
        }
    }

    private static PageResult page(ReportStore.PageResult result) {
        return new PageResult(
                result.items().stream().map(DefaultReportService::summary).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext()
        );
    }

    private static Summary summary(ReportStore.StoredReport report) {
        return new Summary(
                report.id(), report.analysisType(), report.reportType(), report.articleTitle(),
                publisherName(report.snapshot()), report.status(), report.createdAt(), report.updatedAt(),
                report.completedAt(), report.adminReply()
        );
    }

    private static Detail detail(ReportStore.StoredReport report) {
        return new Detail(
                report.id(), report.analysisType(), report.reportType(), report.articleTitle(),
                publisherName(report.snapshot()), report.status(), report.createdAt(), report.updatedAt(),
                report.completedAt(), report.adminReply(), report.description(), report.articleUrl(),
                report.snapshot(), report.version()
        );
    }

    private static String publisherName(Map<String, Object> snapshot) {
        Object articleValue = snapshot.get("article");
        if (articleValue instanceof Map<?, ?> article) {
            Object publisher = article.get("publisher");
            if (publisher != null) {
                return publisher.toString();
            }
        }
        return "";
    }

    private record Snapshot(String articleUrl, String articleTitle, Map<String, Object> value) {
    }
}
