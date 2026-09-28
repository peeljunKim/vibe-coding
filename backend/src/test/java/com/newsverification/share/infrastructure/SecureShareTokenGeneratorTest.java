/* 공유 토큰 생성 규칙 검증 */
package com.newsverification.share.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 256bit Base64URL Token 형식과 임의성 검증 */
class SecureShareTokenGeneratorTest {

    @Test
    void createsDistinctUrlSafeTokensWithoutPadding() {
        SecureShareTokenGenerator generator = new SecureShareTokenGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("[A-Za-z0-9_-]{43}");
        assertThat(second).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(first);
    }
}
