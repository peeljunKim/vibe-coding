/* 문제 신고 Domain 규칙 검증 */
package com.newsverification.report.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 신고 상태 전환과 답변 규칙 검증 */
class AnalysisReportTest {

    @Test
    void allowsOnlyForwardStatusTransitions() {
        assertThat(AnalysisReport.Status.OPEN.canTransitionTo(AnalysisReport.Status.IN_PROGRESS)).isTrue();
        assertThat(AnalysisReport.Status.OPEN.canTransitionTo(AnalysisReport.Status.RESOLVED)).isTrue();
        assertThat(AnalysisReport.Status.IN_PROGRESS.canTransitionTo(AnalysisReport.Status.RESOLVED)).isTrue();
        assertThat(AnalysisReport.Status.RESOLVED.canTransitionTo(AnalysisReport.Status.IN_PROGRESS)).isFalse();
        assertThat(AnalysisReport.Status.OPEN.canTransitionTo(AnalysisReport.Status.OPEN)).isFalse();
    }

    @Test
    void requiresReplyOnlyForResolutionAndUsesCodePointLength() {
        assertThatThrownBy(() -> AnalysisReport.validateTransition(
                AnalysisReport.Status.OPEN, AnalysisReport.Status.RESOLVED, " "
        )).isInstanceOf(IllegalArgumentException.class);

        AnalysisReport.validateTransition(
                AnalysisReport.Status.OPEN, AnalysisReport.Status.IN_PROGRESS, null
        );

        assertThatThrownBy(() -> AnalysisReport.validateAdminReply("😀".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
