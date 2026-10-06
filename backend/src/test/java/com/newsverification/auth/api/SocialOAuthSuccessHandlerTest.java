/* 소셜 OAuth 성공 분기 검증 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 기존 회원 Session과 신규 회원 가입 대기 분기 */
class SocialOAuthSuccessHandlerTest {

    private SocialLoginService socialLoginService;
    private OAuth2AuthorizedClientRepository authorizedClientRepository;
    private SocialOAuthSuccessHandler handler;

    @BeforeEach
    void setUp() {
        socialLoginService = mock(SocialLoginService.class);
        authorizedClientRepository = mock(OAuth2AuthorizedClientRepository.class);
        handler = new SocialOAuthSuccessHandler(
                socialLoginService,
                authorizedClientRepository,
                "http://localhost:5173"
        );
    }

    /** 기존 소셜 회원의 내부 사용자 ID Session 생성 */
    @Test
    void createsApplicationSessionForExistingAccount() throws Exception {
        when(socialLoginService.resolve(any())).thenReturn(
                new SocialLoginService.LoginResolution(true, 42L, "USER", null)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken authentication = googleAuthentication();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(7200);
        assertThat(request.getSession(false).getAttribute(
                org.springframework.security.web.context.HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        )).isNotNull();
        verify(authorizedClientRepository).removeAuthorizedClient(
                "google",
                authentication,
                request,
                response
        );
    }

    /** 기존 일반 계정 이메일의 자동 연결 차단 안내 */
    @Test
    void redirectsExistingEmailToExistingLoginGuidance() throws Exception {
        when(socialLoginService.resolve(any())).thenThrow(
                new com.newsverification.auth.application.SocialLoginException(
                        "SOCIAL_EMAIL_ALREADY_REGISTERED"
                )
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=existing-account");
        assertThat(request.getSession(false)).isNull();
    }

    /** 신규 Provider 사용자의 최소 가입 대기 Session 생성 */
    @Test
    void storesPendingIdentityForNewAccount() throws Exception {
        var pending = new SocialLoginService.ProviderIdentity(
                "GOOGLE",
                "google-subject",
                "user@example.com",
                true
        );
        when(socialLoginService.resolve(any())).thenReturn(
                new SocialLoginService.LoginResolution(false, null, null, pending)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/signup/social/invite");
        assertThat(request.getSession(false).getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isInstanceOf(PendingSocialSignup.class);
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(600);
    }

    /** Naver 중첩 응답의 고유 식별자와 이메일 변환 */
    @Test
    void mapsNaverProfileToProviderIdentity() throws Exception {
        doAnswer(invocation -> {
            SocialLoginService.ProviderIdentity identity = invocation.getArgument(0);
            assertThat(identity.provider()).isEqualTo("naver");
            assertThat(identity.subject()).isEqualTo("naver-subject");
            assertThat(identity.email()).isEqualTo("user@example.com");
            assertThat(identity.emailVerified()).isTrue();
            return new SocialLoginService.LoginResolution(true, 42L, "USER", null);
        }).when(socialLoginService).resolve(any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, naverAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    /** Naver 이메일 미제공의 동의 안내 */
    @Test
    void redirectsNaverProfileWithoutEmailToConsentGuidance() throws Exception {
        doAnswer(invocation -> {
            SocialLoginService.ProviderIdentity identity = invocation.getArgument(0);
            assertThat(identity.provider()).isEqualTo("naver");
            assertThat(identity.subject()).isEqualTo("naver-subject");
            assertThat(identity.email()).isNull();
            assertThat(identity.emailVerified()).isFalse();
            throw new com.newsverification.auth.application.SocialLoginException(
                    "SOCIAL_EMAIL_REQUIRED"
            );
        }).when(socialLoginService).resolve(any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request,
                response,
                naverAuthenticationWithoutEmail()
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=email-required");
        assertThat(request.getSession(false)).isNull();
    }

    /** Kakao 중첩 응답의 고유 식별자와 검증 이메일 변환 */
    @Test
    void mapsKakaoProfileToProviderIdentity() throws Exception {
        doAnswer(invocation -> {
            SocialLoginService.ProviderIdentity identity = invocation.getArgument(0);
            assertThat(identity.provider()).isEqualTo("kakao");
            assertThat(identity.subject()).isEqualTo("123456789");
            assertThat(identity.email()).isEqualTo("user@example.com");
            assertThat(identity.emailVerified()).isTrue();
            return new SocialLoginService.LoginResolution(true, 42L, "USER", null);
        }).when(socialLoginService).resolve(any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication(true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
    }

    /** Kakao 미검증 이메일의 동의 안내 */
    @Test
    void redirectsKakaoProfileWithoutVerifiedEmailToConsentGuidance() throws Exception {
        doAnswer(invocation -> {
            SocialLoginService.ProviderIdentity identity = invocation.getArgument(0);
            assertThat(identity.provider()).isEqualTo("kakao");
            assertThat(identity.subject()).isEqualTo("123456789");
            assertThat(identity.email()).isEqualTo("user@example.com");
            assertThat(identity.emailVerified()).isFalse();
            throw new com.newsverification.auth.application.SocialLoginException(
                    "SOCIAL_EMAIL_REQUIRED"
            );
        }).when(socialLoginService).resolve(any());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication(false));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=email-required");
        assertThat(request.getSession(false)).isNull();
    }

    /** 예기치 않은 계정 처리 오류의 Provider Session 폐기 */
    @Test
    void clearsProviderSessionWhenAccountResolutionFailsUnexpectedly() throws Exception {
        when(socialLoginService.resolve(any())).thenThrow(new IllegalStateException("database unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("provider-state", "authenticated");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(googleAuthentication());

        handler.onAuthenticationSuccess(request, response, googleAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=failed");
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("XSRF-TOKEN=");
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of(
                        "sub", "google-subject",
                        "email", "user@example.com",
                        "email_verified", true
                ),
                "sub"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private OAuth2AuthenticationToken naverAuthentication() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("response", Map.of(
                        "id", "naver-subject",
                        "email", "user@example.com"
                )),
                "response"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "naver");
    }

    private OAuth2AuthenticationToken naverAuthenticationWithoutEmail() {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("response", Map.of("id", "naver-subject")),
                "response"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "naver");
    }

    private OAuth2AuthenticationToken kakaoAuthentication(boolean verified) {
        var principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of(
                        "id", 123456789L,
                        "kakao_account", Map.of(
                                "email", "user@example.com",
                                "is_email_valid", true,
                                "is_email_verified", verified
                        )
                ),
                "id"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }
}
