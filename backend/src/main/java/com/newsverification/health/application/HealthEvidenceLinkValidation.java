/* 건강 근거 링크 검증 결과 */
package com.newsverification.health.application;

import java.util.Optional;

/** Cache 유지·자동 재분석·일시 오류 분기 정보 */
public record HealthEvidenceLinkValidation(
        Status status,
        Optional<HealthAnalysisResult> limitedResult
) {

    /** 검증 결과 필수값과 제한 결과 복사 */
    public HealthEvidenceLinkValidation {
        if (status == null || limitedResult == null) {
            throw new IllegalArgumentException("Evidence link validation is required");
        }
        if (status != Status.MISSING && limitedResult.isPresent()) {
            throw new IllegalArgumentException("Limited result requires missing evidence");
        }
    }

    /** 근거 링크 집계 상태 */
    public enum Status {
        AVAILABLE,
        MISSING,
        TEMPORARY_FAILURE
    }
}
