/* 건강 분석 비동기 HTTP 계약과 보안 검증 */
package com.newsverification.health.api;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.config.SecurityConfig;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthAnalysisServiceUnavailableException;
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

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.net.URI;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비회원·회원 분석 접수와 소유권 조회 경계 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, HealthAnalysisJobControllerTest.SecurityTestConfiguration.class})
class HealthAnalysisJobControllerTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-18T01:00:00Z");
    private static final Instant DEADLINE_AT = ACCEPTED_AT.plusSeconds(90);

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private HealthAnalysisJobService jobService;
    private MockMvc mockMvc;

    /** 보안 Filter와 Mock Application Port 구성 */
    @BeforeEach
    void setUp() {
        jobService = mock(HealthAnalysisJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new HealthAnalysisJobController(Optional.of(jobService))
                )
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 비회원 분석 접수의 CSRF 필수 검증 */
    @Test
    void rejectsGuestSubmissionWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/analyses/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":"https://news.example/article"}
                                """))
                .andExpect(status().isForbidden());

        verify(jobService, never()).accept(any(), any());
    }

    /** 비회원 접수 응답과 작업별 Secret 전달 검증 */
    @Test
    void acceptsGuestSubmissionWithBrowserCookieAndAccessToken() throws Exception {
        var acceptance = new HealthAnalysisJobService.Acceptance(
                "analysis-1",
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.QUEUED,
                new HealthAnalysisJobService.Usage(2, 0, 2, false),
                ACCEPTED_AT,
                DEADLINE_AT,
                "guest-job-token",
                new HealthAnalysisJobService.GuestBrowserCookie(
                        "guest-browser-id",
                        Duration.ofHours(8),
                        false
                )
        );
        when(jobService.accept(eq("https://news.example/article"), any())).thenReturn(acceptance);

        mockMvc.perform(post("/api/analyses/health")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":"https://news.example/article"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/analyses/health/analysis-1"))
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().value("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                .andExpect(cookie().httpOnly("NEWS_VERIFICATION_GUEST", true))
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.stage").value("QUEUED"))
                .andExpect(jsonPath("$.usage.limit").value(2))
                .andExpect(jsonPath("$.pollAfterSeconds").value(2))
                .andExpect(jsonPath("$.guestAccessToken").value("guest-job-token"));
    }

    /** 비회원 Cookie와 작업 Token 기반 진행 상태 조회 검증 */
    @Test
    void getsGuestOwnedProcessingJobWithoutAuthentication() throws Exception {
        var progress = new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.SEARCHING_EVIDENCE,
                DEADLINE_AT,
                null,
                null,
                null,
                null
        );
        when(jobService.find(eq("analysis-1"), any())).thenReturn(Optional.of(progress));

        mockMvc.perform(get("/api/analyses/health/analysis-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "guest-job-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.analysisId").value("analysis-1"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.stage").value("SEARCHING_EVIDENCE"))
                .andExpect(jsonPath("$.pollAfterSeconds").value(2));

        verify(jobService).find(
                "analysis-1",
                new HealthAnalysisJobService.Requester(
                        null,
                        "guest-browser-id",
                        "guest-job-token",
                        "127.0.0.1"
                )
        );
    }

    /** 완료 작업의 결과와 이용량 응답 검증 */
    @Test
    void getsCompletedJobResult() throws Exception {
        var progress = new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.COMPLETED,
                AnalysisJobStage.COMPLETED,
                DEADLINE_AT,
                DEADLINE_AT.plusSeconds(1800),
                mockResult(),
                new HealthAnalysisJobService.Usage(2, 1, 1, true),
                null
        );
        when(jobService.find(eq("analysis-1"), any())).thenReturn(Optional.of(progress));

        mockMvc.perform(get("/api/analyses/health/analysis-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "guest-job-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stage").value("COMPLETED"))
                .andExpect(jsonPath("$.result.overallStatus").value("CAUTION"))
                .andExpect(jsonPath("$.usage.charged").value(true))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.pollAfterSeconds").doesNotExist());
    }

    /** 실패 작업의 내부 분류를 제한한 응답 검증 */
    @Test
    void getsFailedJobError() throws Exception {
        var progress = new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.FAILED,
                AnalysisJobStage.FAILED,
                DEADLINE_AT,
                DEADLINE_AT.plusSeconds(1800),
                null,
                null,
                new HealthAnalysisJobService.Failure(
                        "ARTICLE_FETCH_FAILED",
                        "기사 내용을 가져오지 못했습니다.",
                        null
                )
        );
        when(jobService.find(eq("analysis-1"), any())).thenReturn(Optional.of(progress));

        mockMvc.perform(get("/api/analyses/health/analysis-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "guest-job-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("ARTICLE_FETCH_FAILED"))
                .andExpect(jsonPath("$.error.detail").value("기사 내용을 가져오지 못했습니다."))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.pollAfterSeconds").doesNotExist());
    }

    /** Queue 포화와 Redis 장애의 503 변환 검증 */
    @Test
    void returnsServiceUnavailableWhenAcceptanceCannotReachQueue() throws Exception {
        when(jobService.accept(eq("https://news.example/article"), any()))
                .thenThrow(new HealthAnalysisServiceUnavailableException());

        mockMvc.perform(post("/api/analyses/health")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":"https://news.example/article"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYSIS_SERVICE_UNAVAILABLE"));
    }

    /** 존재·만료·소유권 불일치의 동일한 404 응답 검증 */
    @Test
    void hidesMissingOrUnauthorizedJobAsNotFound() throws Exception {
        when(jobService.find(eq("analysis-1"), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/analyses/health/analysis-1")
                        .cookie(new Cookie("NEWS_VERIFICATION_GUEST", "guest-browser-id"))
                        .header("X-Analysis-Access-Token", "wrong-token"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    /** 인증 회원 식별자의 Application Port 전달 검증 */
    @Test
    void forwardsAuthenticatedMemberIdentity() throws Exception {
        var acceptance = new HealthAnalysisJobService.Acceptance(
                "analysis-2",
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.QUEUED,
                new HealthAnalysisJobService.Usage(5, 1, 4, true),
                ACCEPTED_AT,
                DEADLINE_AT,
                null,
                null
        );
        when(jobService.accept(eq("https://news.example/member-article"), any())).thenReturn(acceptance);

        mockMvc.perform(post("/api/analyses/health")
                        .with(user("test-user").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":"https://news.example/member-article"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.guestAccessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("NEWS_VERIFICATION_GUEST"));

        verify(jobService).accept(
                eq("https://news.example/member-article"),
                eq(new HealthAnalysisJobService.Requester("test-user", null, null, "127.0.0.1"))
        );
    }

    /** 빈 기사 URL의 Application Port 진입 전 거절 검증 */
    @Test
    void rejectsBlankArticleUrl() throws Exception {
        mockMvc.perform(post("/api/analyses/health")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(jobService, never()).accept(any(), any());
    }

    /** Worker Port 미연결 상태의 명시적 서비스 장애 검증 */
    @Test
    void returnsServiceUnavailableWhenWorkerPortIsNotConnected() throws Exception {
        MockMvc unavailableMockMvc = MockMvcBuilders.standaloneSetup(
                        new HealthAnalysisJobController(Optional.empty())
                )
                .apply(springSecurity(springSecurityFilterChain))
                .build();

        unavailableMockMvc.perform(post("/api/analyses/health")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"articleUrl":"https://news.example/article"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ANALYSIS_SERVICE_UNAVAILABLE"));
    }

    /** Mock 분석 완료 결과 */
    private HealthAnalysisResult mockResult() {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"),
                        "건강 기사 제목",
                        "news.example",
                        OffsetDateTime.parse("2026-09-18T09:00:00+09:00"),
                        null
                ),
                ACCEPTED_AT.plusSeconds(3),
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        "건강 기사 제목",
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "Mock 분석에서는 근거 검색을 수행하지 않습니다.",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                false
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
