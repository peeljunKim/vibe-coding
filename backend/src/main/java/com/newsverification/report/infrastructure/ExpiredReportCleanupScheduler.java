/* 만료된 완료 신고 정리 일정 실행 */
package com.newsverification.report.infrastructure;

import com.newsverification.report.application.ReportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 완료 후 30일이 지난 신고의 일일 정리 */
@Component
public class ExpiredReportCleanupScheduler {

    private final ReportService reportService;

    public ExpiredReportCleanupScheduler(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 매일 새벽 완료 신고 정리 */
    @Scheduled(
            cron = "${REPORT_CLEANUP_CRON:0 20 3 * * *}",
            zone = "${app.time-zone:Asia/Seoul}"
    )
    public void cleanupExpiredReports() {
        reportService.cleanupExpiredReports();
    }
}
