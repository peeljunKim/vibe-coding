/* 문제 신고 HTTP 계약과 권한 검증 */
package com.newsverification.report.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.report.application.ReportException;
import com.newsverification.report.application.ReportService;
import com.newsverification.report.domain.AnalysisReport;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원 신고와 관리자 처리의 서버 측 경계 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, ReportControllerTest.SecurityTestConfiguration.class})
class ReportControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private ReportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReportController(service),
                        new AdminReportController(service)
                )
                .setControllerAdvice(new ReportErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    @Test
    void createsReportForAuthenticatedMember() throws Exception {
        when(service.create(any(), any())).thenReturn(detail(17L, AnalysisReport.Status.OPEN, null, 0L));

        mockMvc.perform(post("/api/reports")
                        .with(user("42").roles("USER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "analysisType":"HEALTH",
                                  "analysisId":"analysis-1",
                                  "reportType":"WRONG_JUDGMENT",
                                  "description":"판정을 다시 확인해 주세요."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reports/17"))
                .andExpect(jsonPath("$.id").value("17"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(service).create("42", new ReportService.CreateCommand(
                "HEALTH", "analysis-1", "WRONG_JUDGMENT", "판정을 다시 확인해 주세요."
        ));
    }

    @Test
    void listsOnlyAuthenticatedMembersReports() throws Exception {
        when(service.findMine("42", 0, 20)).thenReturn(page());

        mockMvc.perform(get("/api/reports").with(user("42").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value("17"));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hidesWhetherUserReportExistsOrBelongsToAnotherMember() throws Exception {
        when(service.findOne("42", 99L)).thenThrow(new ReportException("REPORT_NOT_FOUND"));

        mockMvc.perform(get("/api/reports/99").with(user("42").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void allowsOnlyAdminToReadAndUpdateAllReports() throws Exception {
        when(service.findAllAsAdmin(0, 20)).thenReturn(page());
        when(service.updateAsAdmin(any(), any(Long.class), any()))
                .thenReturn(detail(17L, AnalysisReport.Status.RESOLVED, "확인했습니다.", 1L));

        mockMvc.perform(get("/api/admin/reports").with(user("42").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reports").with(user("7").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("17"));

        mockMvc.perform(patch("/api/admin/reports/17")
                        .with(user("7").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"RESOLVED","adminReply":"확인했습니다.","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        verify(service).updateAsAdmin("7", 17L, new ReportService.UpdateCommand(
                "RESOLVED", "확인했습니다.", 0L
        ));
    }

    @Test
    void rejectsAdminUpdateWithoutCsrf() throws Exception {
        mockMvc.perform(patch("/api/admin/reports/17")
                        .with(user("7").roles("ADMIN"))
                        .contentType("application/json")
                        .content("""
                                {"status":"IN_PROGRESS","adminReply":null,"version":0}
                                """))
                .andExpect(status().isForbidden());
    }

    private static ReportService.PageResult page() {
        return new ReportService.PageResult(
                List.of(new ReportService.Summary(
                        17L,
                        AnalysisReport.AnalysisType.HEALTH,
                        AnalysisReport.ReportType.WRONG_JUDGMENT,
                        "건강 기사",
                        "테스트 언론사",
                        AnalysisReport.Status.OPEN,
                        NOW,
                        NOW,
                        null,
                        null
                )),
                0,
                20,
                1,
                1,
                false
        );
    }

    private static ReportService.Detail detail(
            long id,
            AnalysisReport.Status status,
            String reply,
            long version
    ) {
        return new ReportService.Detail(
                id,
                AnalysisReport.AnalysisType.HEALTH,
                AnalysisReport.ReportType.WRONG_JUDGMENT,
                "건강 기사",
                "테스트 언론사",
                status,
                NOW,
                NOW,
                status == AnalysisReport.Status.RESOLVED ? NOW : null,
                reply,
                "판정을 다시 확인해 주세요.",
                "https://news.example/article",
                Map.of("schemaVersion", 1),
                version
        );
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
