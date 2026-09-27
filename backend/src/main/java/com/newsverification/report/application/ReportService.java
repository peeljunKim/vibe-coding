/* 문제 신고 Use Case Port */
package com.newsverification.report.application;

import com.newsverification.report.domain.AnalysisReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 회원 신고와 관리자 처리 경계 */
public interface ReportService {

    Detail create(String memberId, CreateCommand command);

    PageResult findMine(String memberId, int page, int size);

    Detail findOne(String memberId, long reportId);

    PageResult findAllAsAdmin(int page, int size);

    Detail findOneAsAdmin(long reportId);

    Detail updateAsAdmin(String administratorId, long reportId, UpdateCommand command);

    int cleanupExpiredReports();

    record CreateCommand(String analysisType, String analysisId, String reportType, String description) {
    }

    record UpdateCommand(String status, String adminReply, long version) {
    }

    record Summary(
            long id,
            AnalysisReport.AnalysisType analysisType,
            AnalysisReport.ReportType reportType,
            String articleTitle,
            String publisherName,
            AnalysisReport.Status status,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            String adminReply
    ) {
    }

    record Detail(
            long id,
            AnalysisReport.AnalysisType analysisType,
            AnalysisReport.ReportType reportType,
            String articleTitle,
            String publisherName,
            AnalysisReport.Status status,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            String adminReply,
            String description,
            String articleUrl,
            Map<String, Object> resultSnapshot,
            long version
    ) {
        public Detail {
            resultSnapshot = Map.copyOf(resultSnapshot);
        }
    }

    record PageResult(
            List<Summary> items,
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
