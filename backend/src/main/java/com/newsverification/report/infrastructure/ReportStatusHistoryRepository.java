/* 문제 신고 상태 이력 Repository */
package com.newsverification.report.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** 신고 처리 이력 저장 */
public interface ReportStatusHistoryRepository extends JpaRepository<ReportStatusHistoryEntity, Long> {
}
