/* 문제 신고 Spring Data Repository */
package com.newsverification.report.infrastructure;

import com.newsverification.report.domain.AnalysisReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 사용자 소유권과 관리자 목록 조회 */
public interface AnalysisReportRepository extends JpaRepository<AnalysisReportEntity, Long> {

    @EntityGraph(attributePaths = "reporter")
    Page<AnalysisReportEntity> findByReporter_IdOrderByCreatedAtDescIdDesc(long reporterUserId, Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    Optional<AnalysisReportEntity> findByIdAndReporter_Id(long reportId, long reporterUserId);

    @EntityGraph(attributePaths = "reporter")
    Page<AnalysisReportEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    @Query("select report from AnalysisReportEntity report where report.id = :reportId")
    Optional<AnalysisReportEntity> findDetailedById(@Param("reportId") long reportId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AnalysisReportEntity report
            where report.status = :status
              and report.completedAt <= :cutoff
            """)
    int deleteCompletedAtOrBefore(
            @Param("status") AnalysisReport.Status status,
            @Param("cutoff") Instant cutoff
    );
}
