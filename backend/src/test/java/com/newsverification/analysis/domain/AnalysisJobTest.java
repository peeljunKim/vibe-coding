/* 비동기 분석 작업 상태 불변식 검증 */
package com.newsverification.analysis.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 작업 상태와 단계 조합 검증 */
class AnalysisJobTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-15T03:00:00Z");
    private static final AnalysisJobOwner OWNER = new AnalysisJobOwner(
            AnalysisJobOwnerType.MEMBER,
            "member-owner-key"
    );

    /** 완료 상태와 처리 단계의 잘못된 조합 거절 */
    @Test
    void rejectsCompletedStatusWithQueuedStage() {
        assertThatThrownBy(() -> new AnalysisJob(
                "analysis-1",
                OWNER,
                AnalysisJobStatus.COMPLETED,
                AnalysisJobStage.QUEUED,
                ACCEPTED_AT,
                ACCEPTED_AT.plusSeconds(90),
                ACCEPTED_AT.plusSeconds(1800),
                1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 처리 상태와 완료 단계의 잘못된 조합 거절 */
    @Test
    void rejectsProcessingStatusWithCompletedStage() {
        assertThatThrownBy(() -> new AnalysisJob(
                "analysis-1",
                OWNER,
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.COMPLETED,
                ACCEPTED_AT,
                ACCEPTED_AT.plusSeconds(90),
                ACCEPTED_AT.plusSeconds(1800),
                1
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 실패 상태와 처리 단계의 잘못된 조합 거절 */
    @Test
    void rejectsFailedStatusWithQueuedStage() {
        assertThatThrownBy(() -> new AnalysisJob(
                "analysis-1",
                OWNER,
                AnalysisJobStatus.FAILED,
                AnalysisJobStage.QUEUED,
                ACCEPTED_AT,
                ACCEPTED_AT.plusSeconds(90),
                ACCEPTED_AT.plusSeconds(1800),
                1
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
