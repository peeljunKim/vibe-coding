/* 미인증 요청 ProblemDetail 응답 */
package com.newsverification.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 보호 API의 공통 미인증 오류 응답 */
public class AuthenticationProblemEntryPoint implements AuthenticationEntryPoint {

    private static final String RESPONSE_BODY = """
            {"type":"about:blank","title":"Authentication required","status":401,"detail":"로그인이 필요합니다.","code":"AUTHENTICATION_REQUIRED"}
            """.trim();

    /** 인증되지 않은 요청의 ProblemDetail 반환 */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(RESPONSE_BODY);
    }
}
