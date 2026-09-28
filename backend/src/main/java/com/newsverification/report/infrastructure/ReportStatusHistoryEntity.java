/* 문제 신고 상태 이력 JPA 모델 */
package com.newsverification.report.infrastructure;

import com.newsverification.report.domain.AnalysisReport;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 최초 접수와 관리자 상태 변경 이력 */
@Entity
@Table(name = "report_status_history")
public class ReportStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private AnalysisReport.Status fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private AnalysisReport.Status toStatus;

    @Column(name = "admin_reply_snapshot", length = 1000)
    private String adminReplySnapshot;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ReportStatusHistoryEntity() {
    }

    ReportStatusHistoryEntity(
            long reportId,
            AnalysisReport.Status fromStatus,
            AnalysisReport.Status toStatus,
            String adminReplySnapshot,
            Long changedByUserId,
            Instant changedAt
    ) {
        this.reportId = reportId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.adminReplySnapshot = adminReplySnapshot;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
    }
}
