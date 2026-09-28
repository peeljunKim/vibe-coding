/* 문제 신고 Domain 규칙 */
package com.newsverification.report.domain;

/** 신고 유형과 처리 상태 불변 조건 */
public final class AnalysisReport {

    private AnalysisReport() {
    }

    /** 신고 설명 검증 */
    public static String validateDescription(String description) {
        return normalized(description, 2000, "Report description is invalid");
    }

    /** 관리자 답변 검증 */
    public static String validateAdminReply(String adminReply) {
        return normalized(adminReply, 1000, "Admin reply is invalid");
    }

    /** 상태 전환과 완료 답변 검증 */
    public static void validateTransition(Status current, Status target, String adminReply) {
        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException("Report status transition is invalid");
        }
        if (target == Status.RESOLVED) {
            validateAdminReply(adminReply);
        } else if (adminReply != null) {
            validateAdminReply(adminReply);
        }
    }

    private static String normalized(String value, int maximumCodePoints, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maximumCodePoints) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    /** 분석 결과 종류 */
    public enum AnalysisType {
        HEALTH,
        HEADLINE
    }

    /** 사용자 선택 신고 유형 */
    public enum ReportType {
        WRONG_JUDGMENT,
        IRRELEVANT_EVIDENCE,
        BROKEN_EVIDENCE_LINK,
        INACCURATE_HEADLINE,
        UI_OR_FUNCTION_ERROR
    }

    /** 관리자 처리 상태 */
    public enum Status {
        OPEN,
        IN_PROGRESS,
        RESOLVED;

        /** 완료 방향의 단방향 상태 전환 */
        public boolean canTransitionTo(Status target) {
            return switch (this) {
                case OPEN -> target == IN_PROGRESS || target == RESOLVED;
                case IN_PROGRESS -> target == RESOLVED;
                case RESOLVED -> false;
            };
        }
    }
}
