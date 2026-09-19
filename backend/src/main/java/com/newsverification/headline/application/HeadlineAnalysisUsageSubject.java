/* 기사 제목 분석 이용량 식별 입력 */
package com.newsverification.headline.application;

import java.util.List;
import java.util.Objects;

/** 회원·비회원 비식별 Key 묶음 */
public record HeadlineAnalysisUsageSubject(
        HeadlineAnalysisUserType userType,
        List<String> identifierKeys
) {

    /** 이용자 유형과 식별 Key 검증 */
    public HeadlineAnalysisUsageSubject {
        Objects.requireNonNull(userType);
        if (identifierKeys == null || identifierKeys.isEmpty()
                || identifierKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("Headline analysis identifier keys are required");
        }
        identifierKeys = List.copyOf(identifierKeys);
    }
}
