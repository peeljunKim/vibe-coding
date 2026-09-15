/* 비동기 분석 작업 저장 Port */
package com.newsverification.analysis.application;

import com.newsverification.analysis.domain.AnalysisJob;

import java.util.Optional;

/** Redis 구현으로 교체 가능한 조건부 저장 경계 */
public interface AnalysisJobStore {

    /** 중복 없는 신규 작업 저장 */
    boolean create(AnalysisJob job);

    /** TTL을 연장하지 않는 작업 조회 */
    Optional<AnalysisJob> findById(String jobId);

    /** 버전 일치 시 작업 상태 교체 */
    boolean replace(String jobId, long expectedVersion, AnalysisJob updatedJob);
}
