/* 회원 탈퇴 HTTP 인증과 응답 검증 */
package com.newsverification.account.api;

import com.newsverification.account.application.AccountWithdrawalService;
import com.newsverification.config.SecurityConfig;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인증 회원의 탈퇴 신청 경계 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, AccountWithdrawalControllerTest.SecurityTestConfiguration.class})
class AccountWithdrawalControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private AccountWithdrawalService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AccountWithdrawalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountWithdrawalController(service))
                .setControllerAdvice(new AccountWithdrawalErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 로그인 회원의 7일 복구와 37일 삭제 일정 응답 */
    @Test
    void requestsWithdrawalForAuthenticatedMember() throws Exception {
        when(service.request("42")).thenReturn(new AccountWithdrawalService.Withdrawal(
                Instant.parse("2026-10-06T00:00:00Z"),
                Instant.parse("2026-11-05T00:00:00Z")
        ));

        mockMvc.perform(post("/api/account/withdrawal")
                        .with(user("42").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recoveryDeadline").value("2026-10-06T00:00:00Z"))
                .andExpect(jsonPath("$.scheduledDeletionAt").value("2026-11-05T00:00:00Z"));

        verify(service).request("42");
    }

    /** 비로그인 탈퇴 신청 차단 */
    @Test
    void rejectsAnonymousWithdrawalRequest() throws Exception {
        mockMvc.perform(post("/api/account/withdrawal").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /** CSRF 없는 탈퇴 신청 차단 */
    @Test
    void rejectsWithdrawalWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/account/withdrawal")
                        .with(user("42").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
