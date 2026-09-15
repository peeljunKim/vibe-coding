/* 비동기 분석 작업 상태 모델 */
package com.newsverification.analysis.domain;

import java.time.Duration;
import java.time.Instant;

/** 분석 종류와 무관한 작업 수명 정보 */
public record AnalysisJob(
        String id,
        AnalysisJobStatus status,
        AnalysisJobStage stage,
        Instant acceptedAt,
        Instant deadlineAt,
        Instant expiresAt,
        long version
) {

    private static final Duration PROCESSING_DEADLINE = Duration.ofSeconds(90);
    private static final Duration PROCESSING_RETENTION = Duration.ofMinutes(5);
    private static final Duration TERMINAL_RETENTION = Duration.ofMinutes(30);

    /** 대기열 접수 작업 생성 */
    public static AnalysisJob queued(String id, Instant acceptedAt) {
        return new AnalysisJob(
                id,
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.QUEUED,
                acceptedAt,
                acceptedAt.plus(PROCESSING_DEADLINE),
                acceptedAt.plus(PROCESSING_RETENTION),
                0
        );
    }

    /** 허용된 다음 처리 단계 전환 */
    public AnalysisJob advanceTo(AnalysisJobStage nextStage, Instant changedAt) {
        if (!isBeforeDeadline(changedAt)) {
            return this;
        }
        if (!canAdvanceTo(nextStage)) {
            throw new IllegalStateException("Invalid analysis job stage transition");
        }
        return copy(AnalysisJobStatus.PROCESSING, nextStage, expiresAt);
    }

    /** 결과 생성 완료와 종료 상태 만료 설정 */
    public AnalysisJob complete(Instant completedAt) {
        if (status != AnalysisJobStatus.PROCESSING || !isBeforeDeadline(completedAt)) {
            return this;
        }
        if (stage != AnalysisJobStage.GENERATING_RESULT) {
            throw new IllegalStateException("Analysis job is not ready to complete");
        }
        return copy(
                AnalysisJobStatus.COMPLETED,
                AnalysisJobStage.COMPLETED,
                completedAt.plus(TERMINAL_RETENTION)
        );
    }

    /** 처리 실패와 종료 상태 만료 설정 */
    public AnalysisJob fail(Instant failedAt) {
        if (status != AnalysisJobStatus.PROCESSING) {
            return this;
        }
        return copy(
                AnalysisJobStatus.FAILED,
                AnalysisJobStage.FAILED,
                failedAt.plus(TERMINAL_RETENTION)
        );
    }

    /** 현재 시점의 작업 만료 여부 */
    public boolean isExpiredAt(Instant currentTime) {
        return !currentTime.isBefore(expiresAt);
    }

    /** 현재 단계에서 다음 단계 허용 여부 */
    private boolean canAdvanceTo(AnalysisJobStage nextStage) {
        return switch (stage) {
            case QUEUED -> nextStage == AnalysisJobStage.CHECKING_ARTICLE;
            case CHECKING_ARTICLE -> nextStage == AnalysisJobStage.SEARCHING_EVIDENCE
                    || nextStage == AnalysisJobStage.GENERATING_RESULT;
            case SEARCHING_EVIDENCE -> nextStage == AnalysisJobStage.GENERATING_RESULT;
            case GENERATING_RESULT, COMPLETED, FAILED -> false;
        };
    }

    /** 제한 시간 이전 여부 */
    private boolean isBeforeDeadline(Instant changedAt) {
        return changedAt.isBefore(deadlineAt);
    }

    /** 버전 증가 상태 복사 */
    private AnalysisJob copy(
            AnalysisJobStatus nextStatus,
            AnalysisJobStage nextStage,
            Instant nextExpiresAt
    ) {
        return new AnalysisJob(
                id,
                nextStatus,
                nextStage,
                acceptedAt,
                deadlineAt,
                nextExpiresAt,
                version + 1
        );
    }
}
