/* 기사 제목 분석 비동기 HTTP 계약 검증 */
package com.newsverification.headline.api;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.config.SecurityConfig;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비회원 제목 분석 접수와 소유권 조회 경계 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, HeadlineAnalysisJobControllerTest.SecurityTestConfiguration.class})
class HeadlineAnalysisJobControllerTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-19T01:00:00Z");
    private static final Instant DEADLINE_AT = ACCEPTED_AT.plusSeconds(90);

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private HeadlineAnalysisJobService jobService;
    private MockMvc mockMvc;

    /** 보안 Filter와 Mock Application Port 구성 */
    @BeforeEach
    void setUp() {
        jobService = mock(HeadlineAnalysisJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new HeadlineAnalysisJobController(Optional.of(jobService)))
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 비회원 제목 분석 접수의 CSRF 필수 검증 */
    @Test
    void rejectsGuestSubmissionWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/analyses/headline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleUrl\":\"https://news.example/article\"}"))
                .andExpect(status().isForbidden());
        verify(jobService, never()).accept(any(), any());
    }

    /** 비회원 접수 응답과 제목 전용 한도 검증 */
    @Test
    void acceptsGuestSubmissionWithHeadlineUsageLimit() throws Exception {
        var acceptance = new HeadlineAnalysisJobService.Acceptance(
                "headline-1", AnalysisJobStatus.PROCESSING, AnalysisJobStage.QUEUED,
                new HeadlineAnalysisJobService.Usage(5, 0, 5, false),
                ACCEPTED_AT, DEADLINE_AT, "guest-job-token",
                new HeadlineAnalysisJobService.GuestBrowserCookie(
                        "guest-browser-id", Duration.ofHours(8), false)
        );
        when(jobService.accept(eq("https://news.example/article"), any())).thenReturn(acceptance);

        mockMvc.perform(post("/api/analyses/headline")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleUrl\":\"https://news.example/article\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/analyses/headline/headline-1"))
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(cookie().value("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                .andExpect(jsonPath("$.usage.limit").value(5))
                .andExpect(jsonPath("$.guestAccessToken").value("guest-job-token"));
    }

    /** 완료된 제목 분석 결과 응답 검증 */
    @Test
    void getsCompletedHeadlineResult() throws Exception {
        var progress = new HeadlineAnalysisJobService.Progress(
                "headline-1", AnalysisJobStatus.COMPLETED, AnalysisJobStage.COMPLETED,
                DEADLINE_AT, DEADLINE_AT.plusSeconds(1800), result(),
                new HeadlineAnalysisJobService.Usage(5, 1, 4, true), null
        );
        when(jobService.find(eq("headline-1"), any())).thenReturn(Optional.of(progress));

        mockMvc.perform(get("/api/analyses/headline/headline-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "guest-job-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.issues[0].type").value("NO_ISSUE"))
                .andExpect(jsonPath("$.result.alternativeHeadline").doesNotExist())
                .andExpect(jsonPath("$.usage.charged").value(true));
    }

    /** 다른 소유자의 작업 조회 은닉 검증 */
    @Test
    void hidesMissingOrUnauthorizedJobAsNotFound() throws Exception {
        when(jobService.find(eq("headline-1"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/analyses/headline/headline-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "wrong-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    /** 빈 URL의 Application Port 진입 전 거절 검증 */
    @Test
    void rejectsBlankArticleUrl() throws Exception {
        mockMvc.perform(post("/api/analyses/headline")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleUrl\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(jobService, never()).accept(any(), any());
    }

    /** 문제 없음 제목 결과 Fixture */
    private HeadlineAnalysisResult result() {
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"), "기사 제목", "news.example",
                        OffsetDateTime.parse("2026-09-19T09:00:00+09:00"), null
                ),
                ACCEPTED_AT.plusSeconds(3),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE,
                        "제목과 본문의 핵심 내용이 일치합니다."
                )),
                null
        );
    }

    /** 테스트용 Spring Security 사용자 구성 */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        /** HTTP Basic 테스트 사용자 저장소 */
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
