/* 건강 분석 결과 모델 검증 */
package com.newsverification.health.application;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 필수 분석 결과 구조 검증 */
class HealthAnalysisResultTest {

    /** 필수 필드가 없는 Cache JSON 거부 */
    @Test
    void rejectsJsonWithoutRequiredFields() {
        assertThatThrownBy(() -> new ObjectMapper().readValue("{}", HealthAnalysisResult.class))
                .isInstanceOf(JacksonException.class);
    }
}
