/* 저장 건강 분석 Repository */
package com.newsverification.healthrecord.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 건강 분석 기록 조회와 삭제 */
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

    Optional<HealthAnalysisRecordEntity> findByIdAndUserIdAndExpiresAtAfter(
            long id,
            long userId,
            Instant activeAt
    );

    /** 회원의 모든 저장 기록 삭제 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HealthAnalysisRecordEntity record where record.userId = :userId")
    int deleteAllByUserId(@Param("userId") long userId);

    /** 기준 시각 이하 만료 기록 일괄 삭제 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HealthAnalysisRecordEntity record where record.expiresAt <= :cutoff")
    int deleteExpiredAtOrBefore(@Param("cutoff") Instant cutoff);
}
