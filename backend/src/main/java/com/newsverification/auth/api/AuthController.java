/* 일반 로그인 세션 API */
package com.newsverification.auth.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.newsverification.auth.application.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 일반 계정 로그인과 현재 세션 조회 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final int DEFAULT_SESSION_SECONDS = 2 * 60 * 60;
    static final int REMEMBERED_SESSION_SECONDS = 7 * 24 * 60 * 60;

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    /** 기존 Session 폐기 후 인증 Session 생성 */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        LoginService.AuthenticatedAccount account = loginService.authenticate(
                new LoginService.LoginCommand(request.username(), request.password())
        );
        HttpSession previousSession = servletRequest.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }

        int sessionSeconds = request.rememberMe()
                ? REMEMBERED_SESSION_SECONDS
                : DEFAULT_SESSION_SECONDS;
        HttpSession session = servletRequest.getSession(true);
        session.setMaxInactiveInterval(sessionSeconds);

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

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredCsrfCookie())
                .body(new LoginResponse(
                        true,
                        Long.toString(account.userId()),
                        account.username(),
                        account.role(),
                        sessionSeconds
                ));
    }

    /** 현재 인증 Session 요약 */
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        if (!authenticated(authentication)) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(SessionResponse.anonymous());
        }

        HttpSession session = servletRequest.getSession(false);
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("USER");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new SessionResponse(
                        true,
                        authentication.getName(),
                        role,
                        session == null ? null : session.getMaxInactiveInterval()
                ));
    }

    /** 현재 Session과 인증 Context 만료 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCsrfCookie())
                .build();
    }

    private boolean authenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
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

    /** 로그인 입력 */
    public record LoginRequest(
            @NotBlank @Size(max = 20) String username,
            @NotBlank @Size(max = 128) String password,
            boolean rememberMe
    ) {

        @Override
        public String toString() {
            return "LoginRequest[username=[REDACTED], password=[REDACTED], rememberMe="
                    + rememberMe + "]";
        }
    }

    /** 로그인 성공 응답 */
    public record LoginResponse(
            boolean authenticated,
            String userId,
            String username,
            String role,
            int expiresInSeconds
    ) {
    }

    /** 현재 Session 응답 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionResponse(
            boolean authenticated,
            String userId,
            String role,
            Integer expiresInSeconds
    ) {

        private static SessionResponse anonymous() {
            return new SessionResponse(false, null, null, null);
        }
    }
}
