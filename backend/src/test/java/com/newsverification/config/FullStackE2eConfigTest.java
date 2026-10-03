/* Local Full-stack E2E 대체 구성 단위 검증 */
package com.newsverification.config;

import com.newsverification.article.application.ResolvedArticleUrl;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FullStackE2eConfigTest {

    private final FullStackE2eConfig config = new FullStackE2eConfig();

    /** 고정 인증번호와 형식 검증 */
    @Test
    void providesConfiguredSignupCodeOnlyWhenItHasSixDigits() {
        assertThat(config.verificationCodeGenerator("482916").generate()).isEqualTo("482916");
        assertThatThrownBy(() -> config.verificationCodeGenerator("1234"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** DNS와 기사 HTTP 외부 호출 차단 검증 */
    @Test
    void providesDeterministicArticleWithoutExternalNetwork() throws Exception {
        assertThat(config.hostResolver().resolve("e2e.news.invalid"))
                .extracting(address -> address.getHostAddress())
                .containsExactly("1.1.1.1");

        var target = new ResolvedArticleUrl(
                URI.create("https://e2e.news.invalid/article/health"),
                "e2e.news.invalid",
                List.of(config.hostResolver().resolve("e2e.news.invalid").get(0))
        );
        var response = config.articleHttpClient().get(target, Duration.ofSeconds(10), 2 * 1024 * 1024);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.contentType()).startsWith("text/html");
        assertThat(response.body()).contains("매일 걷기는 건강에 도움을 줍니다");
    }
}
