/* 저장 건강 분석 주장 근거 Repository */
package com.newsverification.healthrecord.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** 주장과 근거 관계 저장 */
public interface HealthClaimEvidenceRepository extends JpaRepository<HealthClaimEvidenceEntity, HealthClaimEvidenceId> {
}
