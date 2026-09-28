/* 공유 HTTP 계약과 권한 검증 */
package com.newsverification.share.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.share.application.ShareException;
import com.newsverification.share.application.ShareService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공개 읽기와 회원 생성·해제 보안 경계 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, ShareControllerTest.SecurityTestConfiguration.class})
class ShareControllerTest {

    private static final String TOKEN = "valid-public-share-token";

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private ShareService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ShareService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(service))
                .setControllerAdvice(new ShareErrorHandler())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    @Test
    void allowsAnonymousReadWithoutCsrfAndDisablesCachingAndIndexing() throws Exception {
        when(service.findHeadline(TOKEN)).thenReturn(new ShareService.HeadlineResult(
                "HEADLINE", Instant.parse("2026-10-05T01:00:00Z"),
                new ShareService.Article("https://news.example/article", "기사 제목", "언론사", null, null),
                Instant.parse("2026-09-28T01:00:00Z"),
                List.of(new ShareService.HeadlineIssue("NO_ISSUE", "문제 없음")), null
        ));

        mockMvc.perform(get("/api/shares/headline").header("X-Share-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(jsonPath("$.shareType").value("HEADLINE"));
    }

    @Test
    void requiresAuthenticatedActiveMemberAndCsrfForCreation() throws Exception {
        when(service.createHealth("42", 31L)).thenReturn(new ShareService.Created(
                "HEALTH", TOKEN, Instant.parse("2026-10-05T01:00:00Z")
        ));

        mockMvc.perform(post("/api/health-records/31/shares").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/health-records/31/shares").with(user("42").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/health-records/31/shares")
                        .with(user("42").roles("USER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").value(TOKEN));
    }

    @Test
    void requiresOwnerSessionAndCsrfForRevocation() throws Exception {
        mockMvc.perform(delete("/api/shares/health")
                        .with(user("42").roles("USER"))
                        .header("X-Share-Token", TOKEN))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/shares/health")
                        .with(user("42").roles("USER")).with(csrf())
                        .header("X-Share-Token", TOKEN))
                .andExpect(status().isNoContent());

        verify(service).revokeHealth("42", TOKEN);
    }

    @Test
    void usesSamePublicNotFoundResponseForInvalidShareState() throws Exception {
        when(service.findHealth(TOKEN)).thenThrow(new ShareException("SHARE_NOT_FOUND"));

        mockMvc.perform(get("/api/shares/health").header("X-Share-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("공유 결과를 확인할 수 없습니다."));
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {
    }
}
