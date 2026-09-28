/* 문제 신고 JPA Adapter */
package com.newsverification.report.infrastructure;

import com.newsverification.report.application.ReportException;
import com.newsverification.report.application.ReportStore;
import com.newsverification.report.domain.AnalysisReport;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** 신고 Snapshot과 상태 이력의 Transaction 저장 */
@Component
public class JpaReportStore implements ReportStore {

    private final AnalysisReportRepository reportRepository;
    private final ReportStatusHistoryRepository historyRepository;
    private final UserAccountRepository userRepository;

    public JpaReportStore(
            AnalysisReportRepository reportRepository,
            ReportStatusHistoryRepository historyRepository,
            UserAccountRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public StoredReport save(NewReport report) {
        UserAccount reporter = userRepository.findById(report.reporterUserId())
                .orElseThrow(() -> new ReportException("REPORT_ACCESS_DENIED"));
        AnalysisReportEntity saved = reportRepository.saveAndFlush(new AnalysisReportEntity(reporter, report));
        historyRepository.save(new ReportStatusHistoryEntity(
                saved.id(), null, AnalysisReport.Status.OPEN, null, null, report.createdAt()
        ));
        return stored(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult findAllByReporter(long reporterUserId, int page, int size) {
        return page(reportRepository.findByReporter_IdOrderByCreatedAtDescIdDesc(
                reporterUserId, PageRequest.of(page, size)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredReport> findByReporter(long reportId, long reporterUserId) {
        return reportRepository.findByIdAndReporter_Id(reportId, reporterUserId).map(JpaReportStore::stored);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult findAll(int page, int size) {
        return page(reportRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredReport> findById(long reportId) {
        return reportRepository.findDetailedById(reportId).map(JpaReportStore::stored);
    }

    @Override
    @Transactional
    public StoredReport transition(Transition command) {
        try {
            AnalysisReportEntity report = reportRepository.findDetailedById(command.reportId())
                    .orElseThrow(() -> new ReportException("REPORT_NOT_FOUND"));
            if (report.version() != command.expectedVersion()) {
                throw new ReportException("VERSION_CONFLICT");
            }
            AnalysisReport.Status previous = report.status();
            try {
                report.transition(
                        command.status(), command.adminReply(), command.administratorUserId(),
                        command.completedAt(), command.changedAt()
                );
            } catch (IllegalArgumentException exception) {
                throw new ReportException("REPORT_STATE_CONFLICT");
            }
            AnalysisReportEntity saved = reportRepository.saveAndFlush(report);
            historyRepository.save(new ReportStatusHistoryEntity(
                    saved.id(), previous, command.status(), command.adminReply(),
                    command.administratorUserId(), command.changedAt()
            ));
            historyRepository.flush();
            return stored(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw new ReportException("VERSION_CONFLICT");
        }
    }

    @Override
    @Transactional
    public int deleteResolvedCompletedAtOrBefore(Instant cutoff) {
        return reportRepository.deleteCompletedAtOrBefore(AnalysisReport.Status.RESOLVED, cutoff);
    }

    private static PageResult page(Page<AnalysisReportEntity> page) {
        return new PageResult(
                page.getContent().stream().map(JpaReportStore::stored).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext()
        );
    }

    private static StoredReport stored(AnalysisReportEntity entity) {
        return new StoredReport(
                entity.id(), entity.reporter().id(), entity.reporter().email(), entity.reportType(),
                entity.analysisType(), entity.description(), entity.articleUrl(), entity.articleTitle(),
                entity.resultSnapshot(), entity.status(), entity.adminReply(), entity.completedAt(),
                entity.createdAt(), entity.updatedAt(), entity.version()
        );
    }
}
