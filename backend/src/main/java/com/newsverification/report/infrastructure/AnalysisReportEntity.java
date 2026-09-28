/* 문제 신고 JPA 모델 */
package com.newsverification.report.infrastructure;

import com.newsverification.report.domain.AnalysisReport;
import com.newsverification.signup.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 신고 당시 Snapshot과 현재 처리 상태 */
@Entity
@Table(name = "analysis_reports")
public class AnalysisReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private UserAccount reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private AnalysisReport.ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 20)
    private AnalysisReport.AnalysisType analysisType;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "article_url", nullable = false, length = 2048)
    private String articleUrl;

    @Column(name = "article_title", nullable = false, length = 500)
    private String articleTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_snapshot", nullable = false, columnDefinition = "json")
    private Map<String, Object> resultSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisReport.Status status;

    @Column(name = "admin_reply", length = 1000)
    private String adminReply;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AnalysisReportEntity() {
    }

    /** 새 신고와 불변 Snapshot 구성 */
    AnalysisReportEntity(UserAccount reporter, com.newsverification.report.application.ReportStore.NewReport report) {
        this.reporter = reporter;
        this.reportType = report.reportType();
        this.analysisType = report.analysisType();
        this.description = report.description();
        this.articleUrl = report.articleUrl();
        this.articleTitle = report.articleTitle();
        this.resultSnapshot = new LinkedHashMap<>(report.snapshot());
        this.status = AnalysisReport.Status.OPEN;
        this.createdAt = report.createdAt();
        this.updatedAt = report.createdAt();
    }

    /** 관리자 상태 전환 적용 */
    void transition(
            AnalysisReport.Status target,
            String reply,
            long administratorUserId,
            Instant completedAt,
            Instant changedAt
    ) {
        AnalysisReport.validateTransition(status, target, reply);
        status = target;
        adminReply = reply;
        handledByUserId = administratorUserId;
        this.completedAt = completedAt;
        updatedAt = changedAt;
    }

    Long id() {
        return id;
    }

    UserAccount reporter() {
        return reporter;
    }

    AnalysisReport.ReportType reportType() {
        return reportType;
    }

    AnalysisReport.AnalysisType analysisType() {
        return analysisType;
    }

    String description() {
        return description;
    }

    String articleUrl() {
        return articleUrl;
    }

    String articleTitle() {
        return articleTitle;
    }

    Map<String, Object> resultSnapshot() {
        return Map.copyOf(resultSnapshot);
    }

    AnalysisReport.Status status() {
        return status;
    }

    String adminReply() {
        return adminReply;
    }

    Instant completedAt() {
        return completedAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    long version() {
        return version;
    }
}
