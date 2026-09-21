/* 회원가입 HTTP 계약과 보안 검증 */
package com.newsverification.signup.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.signup.application.SignupException;
import com.newsverification.signup.application.SignupService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비로그인 회원가입과 CSRF 경계 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, SignupControllerTest.SecurityTestConfiguration.class})
class SignupControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private SignupService signupService;
    private MockMvc mockMvc;

    /** 보안 Filter와 Mock Application Port 구성 */
    @BeforeEach
    void setUp() {
        signupService = mock(SignupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SignupController(signupService))
                .setControllerAdvice(new SignupErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 유효한 가입 요청의 미인증 계정 생성 응답 */
    @Test
    void createsPendingAccountForAnonymousUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var command = new SignupService.RegisterCommand(
                "INVITE-2026",
                "health26",
                "test-Password23!",
                "test-Password23!",
                "user@example.com",
                "010-1234-5678",
                true
        );
        when(signupService.register(any())).thenReturn(
                new SignupService.PendingSignup(42L, "health26", "user@example.com", 5, 60)
        );

        mockMvc.perform(post("/api/signup")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteCode":"INVITE-2026",
                                  "username":"health26",
                                  "password":"test-Password23!",
                                  "passwordConfirm":"test-Password23!",
                                  "email":"user@example.com",
                                  "phoneNumber":"010-1234-5678",
                                  "agreementsAccepted":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.remainingAttempts").value(5))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));

        verify(signupService).register(argThat(actual ->
                actual.inviteCode().equals(command.inviteCode())
                        && actual.username().equals(command.username())
                        && actual.email().equals(command.email())
                        && actual.phoneNumber().equals(command.phoneNumber())
                        && actual.agreementsAccepted()
        ));
        assertThat(session.getAttribute(SignupController.PENDING_SIGNUP_USER_ID)).isEqualTo(42L);
    }

    /** 인증번호 확인 후 활성 계정 응답 */
    @Test
    void verifiesEmailCodeForAnonymousUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SignupController.PENDING_SIGNUP_USER_ID, 42L);
        when(signupService.verifyEmail(new SignupService.VerifyCommand(42L, "482916")))
                .thenReturn(new SignupService.CompletedSignup(42L, "health26", "user@example.com"));

        mockMvc.perform(post("/api/signup/email-verification")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":42,"code":"482916"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("health26"))
                .andExpect(jsonPath("$.email").value("user@example.com"));

        assertThat(session.getAttribute(SignupController.PENDING_SIGNUP_USER_ID)).isNull();
    }

    /** 다른 Session의 대기 계정 인증 차단 */
    @Test
    void rejectsVerificationWithoutPendingSignupOwnership() throws Exception {
        mockMvc.perform(post("/api/signup/email-verification")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":42,"code":"482916"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIGNUP_NOT_FOUND"));
    }

    /** 같은 Session의 인증번호 재발급 허용 */
    @Test
    void resendsVerificationForPendingSignupOwner() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SignupController.PENDING_SIGNUP_USER_ID, 42L);
        when(signupService.resendVerification(42L)).thenReturn(
                new SignupService.PendingSignup(42L, "health26", "user@example.com", 5, 60)
        );

        mockMvc.perform(post("/api/signup/email-verification/resend")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":42}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingAttempts").value(5));
    }

    /** 공개 가입 API의 CSRF 누락 거절 */
    @Test
    void rejectsSignupWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /** 계정 중복 항목을 구분하지 않는 공개 오류 */
    @Test
    void returnsGenericConflictForDuplicateAccountData() throws Exception {
        when(signupService.register(any())).thenThrow(new SignupException("DUPLICATE_ACCOUNT"));

        mockMvc.perform(post("/api/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteCode":"INVITE-2026",
                                  "username":"health26",
                                  "password":"test-Password23!",
                                  "passwordConfirm":"test-Password23!",
                                  "email":"user@example.com",
                                  "phoneNumber":"010-1234-5678",
                                  "agreementsAccepted":true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACCOUNT"))
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 계정 정보가 있습니다."));
    }

    /** 민감 가입 입력의 문자열 노출 차단 */
    @Test
    void redactsSensitiveRequestValuesFromStringRepresentation() {
        var request = new SignupController.SignupRequest(
                "INVITE-2026",
                "health26",
                "test-Password23!",
                "test-Password23!",
                "user@example.com",
                "010-1234-5678",
                true
        );

        assertThat(request.toString())
                .doesNotContain("INVITE-2026", "test-Password23!", "user@example.com");
        assertThat(new SignupController.VerificationRequest(42L, "482916").toString())
                .doesNotContain("482916");
    }

    /** 테스트용 Spring Security 구성 */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager();
        }
    }
}
