/* 소셜 OAuth 성공 처리 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginException;
import com.newsverification.auth.application.SocialLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Provider Session 제거 후 내부 회원 Session 전환 */
public class SocialOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialOAuthSuccessHandler.class);
    private static final int APPLICATION_SESSION_SECONDS = 2 * 60 * 60;
    private static final int PENDING_SIGNUP_SESSION_SECONDS = 10 * 60;

    private final SocialLoginService socialLoginService;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final String frontendBaseUrl;

    public SocialOAuthSuccessHandler(
            SocialLoginService socialLoginService,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            String frontendBaseUrl
    ) {
        this.socialLoginService = socialLoginService;
        this.authorizedClientRepository = authorizedClientRepository;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;

        try {
            authorizedClientRepository.removeAuthorizedClient(
                    oauth.getAuthorizedClientRegistrationId(),
                    oauth,
                    request,
                    response
            );
            SocialLoginService.LoginResolution resolution = socialLoginService.resolve(identity(oauth));
            invalidateCurrentSession(request);
            expireCsrfCookie(response);
            if (resolution.existingAccount()) {
                createAuthenticatedSession(request, resolution.userId(), resolution.role());
                response.sendRedirect(frontendBaseUrl + "/");
                return;
            }
            createPendingSignupSession(request, resolution.pendingIdentity());
            response.sendRedirect(frontendBaseUrl + "/signup/social/invite");
        } catch (SocialLoginException exception) {
            invalidateCurrentSession(request);
            expireCsrfCookie(response);
            response.sendRedirect(frontendBaseUrl + loginErrorPath(exception.code()));
        } catch (RuntimeException exception) {
            invalidateCurrentSession(request);
            expireCsrfCookie(response);
            log.error(
                    "OAuth success processing failed: provider={}, category=internal_error, cause={}",
                    oauth.getAuthorizedClientRegistrationId(),
                    exception.getClass().getSimpleName()
            );
            response.sendRedirect(frontendBaseUrl + "/login?oauth=failed");
        }
    }

    private SocialLoginService.ProviderIdentity identity(OAuth2AuthenticationToken authentication) {
        OAuth2User principal = authentication.getPrincipal();
        if ("naver".equalsIgnoreCase(authentication.getAuthorizedClientRegistrationId())) {
            return naverIdentity(authentication, principal);
        }
        Object verified = principal.getAttribute("email_verified");
        return new SocialLoginService.ProviderIdentity(
                authentication.getAuthorizedClientRegistrationId(),
                principal.getAttribute("sub"),
                principal.getAttribute("email"),
                Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified))
        );
    }

    private SocialLoginService.ProviderIdentity naverIdentity(
            OAuth2AuthenticationToken authentication,
            OAuth2User principal
    ) {
        Object response = principal.getAttribute("response");
        if (!(response instanceof Map<?, ?> attributes)) {
            return new SocialLoginService.ProviderIdentity(
                    authentication.getAuthorizedClientRegistrationId(),
                    null,
                    null,
                    false
            );
        }
        String subject = stringValue(attributes.get("id"));
        String email = stringValue(attributes.get("email"));
        return new SocialLoginService.ProviderIdentity(
                authentication.getAuthorizedClientRegistrationId(),
                subject,
                email,
                email != null && !email.isBlank()
        );
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private void createAuthenticatedSession(
            HttpServletRequest request,
            long userId,
            String role
    ) {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(APPLICATION_SESSION_SECONDS);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                Long.toString(userId),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );
    }

    private void createPendingSignupSession(
            HttpServletRequest request,
            SocialLoginService.ProviderIdentity identity
    ) {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(PENDING_SIGNUP_SESSION_SECONDS);
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, PendingSocialSignup.from(identity));
        SecurityContextHolder.clearContext();
    }

    private void invalidateCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private String loginErrorPath(String code) {
        return switch (code) {
            case "SOCIAL_EMAIL_REQUIRED" -> "/login?oauth=email-required";
            case "SOCIAL_EMAIL_ALREADY_REGISTERED" -> "/login?oauth=existing-account";
            default -> "/login?oauth=failed";
        };
    }

    private void expireCsrfCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", ResponseCookie.from("XSRF-TOKEN", "")
                .path("/")
                .httpOnly(false)
                .sameSite("Lax")
                .maxAge(0)
                .build()
                .toString());
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
