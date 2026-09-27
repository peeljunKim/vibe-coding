/* 문제 신고 Persistence Port */
package com.newsverification.report.application;

import com.newsverification.report.domain.AnalysisReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 신고 Snapshot과 처리 상태 영속 경계 */
public interface ReportStore {

    StoredReport save(NewReport report);

    PageResult findAllByReporter(long reporterUserId, int page, int size);

    Optional<StoredReport> findByReporter(long reportId, long reporterUserId);

    PageResult findAll(int page, int size);

    Optional<StoredReport> findById(long reportId);

    StoredReport transition(Transition command);

    int deleteResolvedCompletedAtOrBefore(Instant cutoff);

    record NewReport(
            long reporterUserId,
            AnalysisReport.ReportType reportType,
            AnalysisReport.AnalysisType analysisType,
            String description,
            String articleUrl,
            String articleTitle,
            Map<String, Object> snapshot,
            Instant createdAt
    ) {
        public NewReport {
            snapshot = Map.copyOf(snapshot);
        }
    }

    record Transition(
            long reportId,
            long administratorUserId,
            AnalysisReport.Status status,
            String adminReply,
            Instant completedAt,
            Instant changedAt,
            long expectedVersion
    ) {
    }

    record StoredReport(
            long id,
            long reporterUserId,
            String reporterEmail,
            AnalysisReport.ReportType reportType,
            AnalysisReport.AnalysisType analysisType,
            String description,
            String articleUrl,
            String articleTitle,
            Map<String, Object> snapshot,
            AnalysisReport.Status status,
            String adminReply,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        public StoredReport {
            snapshot = Map.copyOf(snapshot);
        }
    }

    record PageResult(
            List<StoredReport> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public PageResult {
            items = List.copyOf(items);
        }
    }
}
