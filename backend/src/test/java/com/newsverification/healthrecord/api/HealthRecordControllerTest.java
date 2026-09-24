/* 저장 건강 분석 HTTP 계약과 인증 검증 */
package com.newsverification.healthrecord.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.healthrecord.application.HealthRecordException;
import com.newsverification.healthrecord.application.HealthRecordService;
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
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인 회원의 명시적 저장과 목록 조회 경계 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, HealthRecordControllerTest.SecurityTestConfiguration.class})
class HealthRecordControllerTest {

    private static final Instant ANALYZED_AT = Instant.parse("2026-09-24T01:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-24T01:00:00Z");

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private HealthRecordService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(HealthRecordService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthRecordController(service))
                .setControllerAdvice(new HealthRecordErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 완료된 회원 분석의 명시적 저장 */
    @Test
    void savesCompletedMemberAnalysis() throws Exception {
        HealthRecordService.Summary saved = summary(31L);
        when(service.save("42", "analysis-1")).thenReturn(saved);

        mockMvc.perform(post("/api/health-records")
                        .with(user("42").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"analysisId":"analysis-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("31"))
                .andExpect(jsonPath("$.title").value("건강 기사 제목"))
                .andExpect(jsonPath("$.overallStatus").value("CAUTION"))
                .andExpect(jsonPath("$.analyzedAt").value(ANALYZED_AT.toString()))
                .andExpect(jsonPath("$.expiresAt").value(EXPIRES_AT.toString()));

        verify(service).save("42", "analysis-1");
    }

    /** 최신 분석일부터 정렬된 회원 기록 페이지 조회 */
    @Test
    void listsOnlyAuthenticatedMemberRecords() throws Exception {
        when(service.findAll("42", 0, 20)).thenReturn(new HealthRecordService.PageResult(
                List.of(summary(31L)), 0, 20, 1, 1, false
        ));

        mockMvc.perform(get("/api/health-records")
                        .with(user("42").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("31"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /** 비로그인 저장 요청 차단 */
    @Test
    void rejectsAnonymousSave() throws Exception {
        mockMvc.perform(post("/api/health-records")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"analysisId":"analysis-1"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    /** 비로그인 목록 요청 차단 */
    @Test
    void rejectsAnonymousList() throws Exception {
        mockMvc.perform(get("/api/health-records"))
                .andExpect(status().isUnauthorized());
    }

    /** 빈 분석 식별자의 요청 형식 오류 */
    @Test
    void rejectsBlankAnalysisId() throws Exception {
        mockMvc.perform(post("/api/health-records")
                        .with(user("42").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"analysisId":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 진행 중 분석의 저장 충돌 응답 */
    @Test
    void returnsConflictWhenAnalysisIsNotCompleted() throws Exception {
        when(service.save("42", "analysis-1"))
                .thenThrow(new HealthRecordException("ANALYSIS_NOT_COMPLETED"));

        mockMvc.perform(post("/api/health-records")
                        .with(user("42").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"analysisId":"analysis-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_COMPLETED"));
    }

    /** 최대 크기를 넘는 목록 요청 거절 */
    @Test
    void rejectsInvalidPagination() throws Exception {
        when(service.findAll("42", 0, 101))
                .thenThrow(new HealthRecordException("INVALID_PAGINATION"));

        mockMvc.perform(get("/api/health-records?page=0&size=101")
                        .with(user("42").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"));
    }

    private HealthRecordService.Summary summary(long id) {
        return new HealthRecordService.Summary(
                id,
                "건강 기사 제목",
                "CAUTION",
                ANALYZED_AT,
                EXPIRES_AT
        );
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
