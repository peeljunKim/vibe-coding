/* 저장 건강 분석 주장 Repository */
package com.newsverification.healthrecord.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/** 핵심 주장 저장 */
public interface HealthClaimRepository extends JpaRepository<HealthClaimEntity, Long> {
}
