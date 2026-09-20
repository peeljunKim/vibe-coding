/* 비동기 분석 종료 결과 저장 Port */
package com.newsverification.analysis.application;

import com.newsverification.analysis.domain.AnalysisJob;

import java.util.Optional;

/** 상태 전환과 결과 저장의 원자 경계 */
public interface AnalysisJobOutcomeStore {

    /** Version 일치 시 종료 상태와 결과 교체 */
    boolean replaceWithOutcome(
            String jobId,
            long expectedVersion,
            AnalysisJob updatedJob,
            AnalysisJobOutcome outcome
    );

    /** TTL을 연장하지 않는 종료 결과 조회 */
    Optional<AnalysisJobOutcome> findOutcome(String jobId);
}
