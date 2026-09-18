/* 비동기 분석 작업 종료 결과 */
package com.newsverification.analysis.application;

/** Redis 작업 상태와 원자적으로 저장하는 결과 또는 실패 */
public record AnalysisJobOutcome(
        Type type,
        String resultJson,
        String errorCode,
        String errorDetail,
        String usageJson
) {

    /** 종료 결과 조합 검증 */
    public AnalysisJobOutcome {
        if (type == null) {
            throw new IllegalArgumentException("Analysis job outcome type is required");
        }
        if (type == Type.RESULT && isBlank(resultJson)) {
            throw new IllegalArgumentException("Completed analysis result is required");
        }
        if (type == Type.FAILURE && (isBlank(errorCode) || isBlank(errorDetail))) {
            throw new IllegalArgumentException("Failed analysis error is required");
        }
    }

    /** 완료 결과 생성 */
    public static AnalysisJobOutcome completed(String resultJson, String usageJson) {
        return new AnalysisJobOutcome(Type.RESULT, resultJson, null, null, usageJson);
    }

    /** 실패 결과 생성 */
    public static AnalysisJobOutcome failed(String errorCode, String errorDetail, String usageJson) {
        return new AnalysisJobOutcome(Type.FAILURE, null, errorCode, errorDetail, usageJson);
    }

    /** 빈 문자열 확인 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 종료 결과 유형 */
    public enum Type {
        RESULT,
        FAILURE
    }
}
