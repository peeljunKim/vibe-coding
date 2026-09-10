package com.newsverification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Backend 시작 구성 검증 */
class NewsVerificationApplicationTest {

    /** 애플리케이션 클래스 존재 확인 */
    @Test
    void applicationClassExists() {
        assertThat(NewsVerificationApplication.class).isNotNull();
    }
}
