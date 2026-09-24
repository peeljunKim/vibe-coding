/* 저장 건강 분석 Repository */
package com.newsverification.healthrecord.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/** 중복 결과 확인과 회원별 최신 기록 조회 */
public interface HealthAnalysisRecordRepository extends JpaRepository<HealthAnalysisRecordEntity, Long> {

    Optional<HealthAnalysisRecordEntity> findByUserIdAndNormalizedUrlDigestAndAnalyzedAt(
            long userId,
            byte[] normalizedUrlDigest,
            Instant analyzedAt
    );

    Page<HealthAnalysisRecordEntity> findByUserIdAndExpiresAtAfterOrderByAnalyzedAtDescIdDesc(
            long userId,
            Instant activeAt,
            Pageable pageable
    );
}
