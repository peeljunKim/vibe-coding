/* Local Full-stack E2E 외부 경계 대체 구성 */
package com.newsverification.config;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.HostResolver;
import com.newsverification.signup.application.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 외부 네트워크 없는 실제 Local 경계 검증 지원 */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class FullStackE2eConfig {

    private static final String ARTICLE_TITLE = "매일 걷기는 건강에 도움을 줍니다";
    private static final String ARTICLE_HTML = """
            <!doctype html>
            <html lang="ko">
            <head>
              <meta property="og:title" content="%s">
              <meta property="article:published_time" content="2026-10-03T09:00:00+09:00">
              <meta property="article:modified_time" content="2026-10-03T10:00:00+09:00">
            </head>
            <body>
              <article itemprop="articleBody">
                <p>건강을 위해 규칙적으로 걷는 습관은 일상적인 신체 활동에 도움을 줍니다.</p>
                <p>개인의 질병과 치료 상태에 따라 의료진과 상담해 운동 계획을 조정해야 합니다.</p>
              </article>
            </body>
            </html>
            """.formatted(ARTICLE_TITLE);

    /** Browser E2E 전용 고정 인증번호 */
    @Bean
    VerificationCodeGenerator verificationCodeGenerator(
            @Value("${E2E_SIGNUP_CODE:}") String verificationCode
    ) {
        if (!verificationCode.matches("[0-9]{6}")) {
            throw new IllegalStateException("E2E_SIGNUP_CODE must contain exactly six digits");
        }
        return () -> verificationCode;
    }

    /** 외부 DNS 조회 없는 공인 주소 결과 */
    @Bean
    HostResolver hostResolver() {
        return hostname -> List.of(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
    }

    /** 외부 기사 요청 없는 고정 건강 기사 응답 */
    @Bean
    ArticleHttpClient articleHttpClient() {
        byte[] responseBytes = ARTICLE_HTML.getBytes(StandardCharsets.UTF_8);
        return (target, timeout, maxResponseBytes) -> new ArticleHttpResponse(
                200,
                "text/html; charset=UTF-8",
                ARTICLE_HTML,
                responseBytes.length,
                null
        );
    }
}
