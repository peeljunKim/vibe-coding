/* 소셜 초대 가입 HTTP 계약 검증 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginException;
import com.newsverification.auth.application.SocialLoginService;
import com.newsverification.config.SecurityConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가입 대기 소유권과 CSRF 경계 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, SocialSignupControllerTest.SecurityTestConfiguration.class})
class SocialSignupControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private SocialLoginService socialLoginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        socialLoginService = mock(SocialLoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SocialSignupController(socialLoginService))
                .setControllerAdvice(new SocialSignupErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 가입 대기 신원과 초대 코드로 활성 Session 생성 */
    @Test
    void completesPendingSocialSignup() throws Exception {
        when(socialLoginService.completeSignup(any())).thenReturn(
                new SocialLoginService.AuthenticatedAccount(42L, "USER")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, new PendingSocialSignup(
                "GOOGLE",
                "google-subject",
                "user@example.com"
        ));

        var result = mockMvc.perform(post("/api/signup/social")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INVITE-2026","agreementsAccepted":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.expiresInSeconds").value(7200))
                .andReturn();

        assertThat(session.isInvalid()).isTrue();
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    /** 가입 대기 Session 없는 요청의 정보 비노출 */
    @Test
    void rejectsMissingPendingIdentity() throws Exception {
        mockMvc.perform(post("/api/signup/social")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INVITE-2026","agreementsAccepted":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SOCIAL_SIGNUP_NOT_FOUND"));
    }

    /** 소셜 가입 POST의 CSRF 누락 차단 */
    @Test
    void rejectsRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/signup/social")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /** 내부 가입 충돌의 안전한 공통 응답 */
    @Test
    void hidesInternalSocialAccountConflict() throws Exception {
        when(socialLoginService.completeSignup(any()))
                .thenThrow(new SocialLoginException("SOCIAL_ACCOUNT_CONFLICT"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, new PendingSocialSignup(
                "GOOGLE",
                "google-subject",
                "user@example.com"
        ));

        mockMvc.perform(post("/api/signup/social")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INVITE-2026","agreementsAccepted":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_CONFLICT"));
    }

    /** 가입 직전 기존 이메일 충돌의 안전한 로그인 안내 */
    @Test
    void guidesExistingEmailAccountToOriginalLogin() throws Exception {
        when(socialLoginService.completeSignup(any()))
                .thenThrow(new SocialLoginException("SOCIAL_EMAIL_ALREADY_REGISTERED"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, new PendingSocialSignup(
                "GOOGLE",
                "google-subject",
                "user@example.com"
        ));

        mockMvc.perform(post("/api/signup/social")
                        .session(session)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"inviteCode":"INVITE-2026","agreementsAccepted":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.detail").value(
                        "이미 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요."
                ));
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
