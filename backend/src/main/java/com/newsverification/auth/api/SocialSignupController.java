/* 소셜 초대 가입 API */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginException;
import com.newsverification.auth.application.SocialLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Provider 인증 완료 사용자의 초대 코드 가입 */
@RestController
@RequestMapping("/api/signup/social")
public class SocialSignupController {

    private static final int SESSION_SECONDS = 2 * 60 * 60;

    private final SocialLoginService socialLoginService;

    public SocialSignupController(SocialLoginService socialLoginService) {
        this.socialLoginService = socialLoginService;
    }

    /** 가입 대기 신원 확인과 활성 계정 Session 생성 */
    @PostMapping
    public ResponseEntity<SocialSignupResponse> complete(
            @Valid @RequestBody SocialSignupRequest request,
            HttpServletRequest servletRequest
    ) {
        HttpSession pendingSession = servletRequest.getSession(false);
        Object value = pendingSession == null
                ? null
                : pendingSession.getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialSignup pending)) {
            throw new SocialLoginException("SOCIAL_SIGNUP_NOT_FOUND");
        }

        SocialLoginService.AuthenticatedAccount account = socialLoginService.completeSignup(
                new SocialLoginService.CompleteSignupCommand(
                        pending.toIdentity(),
                        request.inviteCode(),
                        request.agreementsAccepted()
                )
        );
        pendingSession.invalidate();
        SecurityContextHolder.clearContext();

        HttpSession session = servletRequest.getSession(true);
        session.setMaxInactiveInterval(SESSION_SECONDS);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                Long.toString(account.userId()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role()))
        );
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredCsrfCookie())
                .body(new SocialSignupResponse(
                        true,
                        Long.toString(account.userId()),
                        account.role(),
                        SESSION_SECONDS
                ));
    }

    private String expiredCsrfCookie() {
        return ResponseCookie.from("XSRF-TOKEN", "")
                .path("/")
                .httpOnly(false)
                .sameSite("Lax")
                .maxAge(0)
                .build()
                .toString();
    }

    /** 소셜 초대 가입 입력 */
    public record SocialSignupRequest(
            @NotBlank @Size(max = 100) String inviteCode,
            @AssertTrue boolean agreementsAccepted
    ) {

        @Override
        public String toString() {
            return "SocialSignupRequest[redacted]";
        }
    }

    /** 소셜 가입 완료 Session 요약 */
    public record SocialSignupResponse(
            boolean authenticated,
            String userId,
            String role,
            int expiresInSeconds
    ) {
    }
}
