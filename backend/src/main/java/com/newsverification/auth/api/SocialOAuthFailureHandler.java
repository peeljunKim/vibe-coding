/* 소셜 OAuth 실패 처리 */
package com.newsverification.auth.api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/** 사용자 취소와 Provider 인증 실패의 안전한 Redirect */
public class SocialOAuthFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialOAuthFailureHandler.class);

    private final String frontendBaseUrl;

    public SocialOAuthFailureHandler(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        boolean cancelled = exception instanceof OAuth2AuthenticationException oauthException
                && "access_denied".equals(oauthException.getError().getErrorCode());
        String provider = providerFrom(request);
        if (cancelled) {
            log.info("OAuth authentication cancelled: provider={}", provider);
        } else {
            log.warn(
                    "OAuth authentication failed: provider={}, category=provider_authentication",
                    provider
            );
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        response.sendRedirect(frontendBaseUrl + (cancelled
                ? "/login?oauth=cancelled"
                : "/login?oauth=failed"));
    }

    private String providerFrom(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri.endsWith("/naver")) {
            return "naver";
        }
        if (requestUri.endsWith("/google")) {
            return "google";
        }
        return "unknown";
    }
}
