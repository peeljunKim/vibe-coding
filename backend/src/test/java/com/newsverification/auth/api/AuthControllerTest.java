/* 일반 로그인 HTTP 계약과 Session 보안 검증 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.LoginException;
import com.newsverification.auth.application.LoginService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인·조회·로그아웃 Session 수명주기 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, AuthControllerTest.SecurityTestConfiguration.class})
class AuthControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private LoginService loginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(loginService))
                .setControllerAdvice(new AuthErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 기본 로그인 Session의 2시간 수명과 인증 조회 */
    @Test
    void createsTwoHourSessionAndReturnsAuthenticatedState() throws Exception {
        when(loginService.authenticate(any())).thenReturn(account());

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"health26","password":"Password!23","rememberMe":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.expiresInSeconds").value(7200))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(7200);

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("42"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    /** 로그인 유지 선택의 7일 Session 수명 */
    @Test
    void createsSevenDaySessionWhenRemembered() throws Exception {
        when(loginService.authenticate(any())).thenReturn(account());

        var result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"health26","password":"Password!23","rememberMe":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(604800))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(604800);
    }

    /** 로그인 POST의 CSRF 누락 차단 */
    @Test
    void rejectsLoginWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /** 잠금 오류의 공개 메시지와 재시도 시간 */
    @Test
    void returnsLockedAccountProblem() throws Exception {
        when(loginService.authenticate(any())).thenThrow(new LoginException("LOGIN_LOCKED", 1800));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"health26","password":"Password!23","rememberMe":false}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1800"))
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    /** 로그아웃의 Session 무효화 */
    @Test
    void invalidatesSessionOnLogout() throws Exception {
        when(loginService.authenticate(any())).thenReturn(account());
        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"username":"health26","password":"Password!23","rememberMe":false}
                                """))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    /** 비로그인 Session 조회의 비인증 응답 */
    @Test
    void returnsAnonymousSessionState() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    private LoginService.AuthenticatedAccount account() {
        return new LoginService.AuthenticatedAccount(42L, "health26", "USER");
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
