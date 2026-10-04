/* 일일 이용량 공개 API와 식별 경계 검증 */
package com.newsverification.usage.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.usage.application.DailyUsageService;
import com.newsverification.usage.application.DailyUsageServiceUnavailableException;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원·비회원 일일 이용량 HTTP 계약 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, DailyUsageControllerTest.SecurityTestConfiguration.class})
class DailyUsageControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private DailyUsageService dailyUsageService;
    private MockMvc mockMvc;

    /** 보안 Filter와 Mock Application Port 구성 */
    @BeforeEach
    void setUp() {
        dailyUsageService = mock(DailyUsageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DailyUsageController(dailyUsageService))
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 기존 비회원 Cookie 기준의 기능별 이용량 공개 조회 */
    @Test
    void getsGuestDailyUsageWithoutAuthentication() throws Exception {
        when(dailyUsageService.get(any())).thenReturn(snapshot(null));

        mockMvc.perform(get("/api/usage")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser"))
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.resetsAt").value("2026-10-04T15:00:00Z"))
                .andExpect(jsonPath("$.health.limit").value(2))
                .andExpect(jsonPath("$.health.used").value(1))
                .andExpect(jsonPath("$.health.remaining").value(1))
                .andExpect(jsonPath("$.headline.limit").value(5))
                .andExpect(jsonPath("$.headline.used").value(2))
                .andExpect(jsonPath("$.headline.remaining").value(3));

        verify(dailyUsageService).get(new DailyUsageService.Requester(
                null, "guest-browser", "203.0.113.10"
        ));
    }

    /** Cookie 없는 비회원의 동일 기능용 브라우저 Cookie 발급 */
    @Test
    void issuesGuestBrowserCookieForFirstUsageLookup() throws Exception {
        var guestCookie = new DailyUsageService.GuestBrowserCookie(
                "new-guest-browser", Duration.ofHours(9), false
        );
        when(dailyUsageService.get(any())).thenReturn(snapshot(guestCookie));

        mockMvc.perform(get("/api/usage"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("NEWS_VERIFICATION_GUEST", "new-guest-browser"))
                .andExpect(cookie().httpOnly("NEWS_VERIFICATION_GUEST", true))
                .andExpect(cookie().sameSite("NEWS_VERIFICATION_GUEST", "Lax"));
    }

    /** 인증 회원 이용량 조회의 비회원 Cookie 미발급 */
    @Test
    void getsMemberDailyUsageWithoutGuestCookie() throws Exception {
        when(dailyUsageService.get(any())).thenReturn(snapshot(null));

        mockMvc.perform(get("/api/usage").with(user("member-1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(dailyUsageService).get(new DailyUsageService.Requester(
                "member-1", null, "127.0.0.1"
        ));
    }

    /** Redis 이용량 조회 장애의 503 공통 오류 응답 */
    @Test
    void returnsServiceUnavailableWhenUsageCannotBeRead() throws Exception {
        when(dailyUsageService.get(any())).thenThrow(new DailyUsageServiceUnavailableException());

        mockMvc.perform(get("/api/usage"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("USAGE_SERVICE_UNAVAILABLE"));
    }

    /** 기능별 이용량 응답 Fixture */
    private DailyUsageService.Snapshot snapshot(DailyUsageService.GuestBrowserCookie guestCookie) {
        return new DailyUsageService.Snapshot(
                "Asia/Seoul",
                Instant.parse("2026-10-04T15:00:00Z"),
                new DailyUsageService.Counter(2, 1, 1),
                new DailyUsageService.Counter(5, 2, 3),
                guestCookie
        );
    }

    /** 테스트용 Spring Security 사용자 구성 */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        /** HTTP Basic 테스트 사용자 저장소 */
        @Bean
        UserDetailsService userDetailsService() {
            var user = User.withUsername("member-1")
                    .password("{noop}test-password")
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(user);
        }
    }
}
