/* 저장 건강 분석 근거 Repository */
package com.newsverification.healthrecord.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** 근거 스냅샷 저장 */
public interface HealthEvidenceRepository extends JpaRepository<HealthEvidenceEntity, Long> {
}
