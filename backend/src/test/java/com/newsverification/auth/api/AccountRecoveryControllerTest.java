/* 계정 복구 HTTP 계약 검증 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.AccountRecoveryException;
import com.newsverification.auth.application.AccountRecoveryService;
import com.newsverification.config.SecurityConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비로그인 계정 복구 API와 공개 오류 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        AccountRecoveryControllerTest.SecurityTestConfiguration.class
})
class AccountRecoveryControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private AccountRecoveryService recoveryService;

    private MockMvc mockMvc;

    /** 보안 Filter와 계정 복구 Controller 구성 */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AccountRecoveryController(recoveryService),
                        new AccountRecoveryErrorHandler()
                )
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 비로그인 아이디 찾기 인증번호 요청 */
    @Test
    void acceptsUsernameRecoveryCodeRequestForGuest() throws Exception {
        when(recoveryService.requestUsernameCode("user@example.com"))
                .thenReturn(new AccountRecoveryService.RecoveryRequest(5, 60));

        mockMvc.perform(post("/api/auth/recovery/username/code")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.remainingAttempts").value(5))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));
    }

    /** 인증번호 없는 요청의 CSRF 차단 */
    @Test
    void requiresCsrfForRecoveryRequest() throws Exception {
        mockMvc.perform(post("/api/auth/recovery/password/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    /** 비밀번호 재설정 완료 응답 */
    @Test
    void resetsPasswordForVerifiedRequest() throws Exception {
        mockMvc.perform(post("/api/auth/recovery/password/reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"user@example.com",
                                  "code":"482916",
                                  "password":"test-Password23!",
                                  "passwordConfirm":"test-Password23!"
                                }
                                """))
                .andExpect(status().isNoContent());
    }

    /** 인증 실패의 안전한 공개 오류 */
    @Test
    void returnsGenericVerificationFailure() throws Exception {
        when(recoveryService.verifyUsernameCode(any(), any()))
                .thenThrow(new AccountRecoveryException("INVALID_VERIFICATION_CODE"));

        mockMvc.perform(post("/api/auth/recovery/username/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"))
                .andExpect(jsonPath("$.detail").value("인증번호를 확인해 주세요."));
    }

    /** 테스트용 계정 복구 Service와 Security 사용자 */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        @Bean
        AccountRecoveryService accountRecoveryService() {
            return mock(AccountRecoveryService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            var user = User.withUsername("test-user")
                    .password("{noop}test-password")
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(user);
        }
    }
}
