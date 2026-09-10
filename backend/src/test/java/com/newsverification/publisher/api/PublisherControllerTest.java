/* 지원 언론사 HTTP와 공개 접근 검증 */
package com.newsverification.publisher.api;

import com.newsverification.config.SecurityConfig;
import com.newsverification.publisher.application.PublisherService;
import com.newsverification.publisher.application.SupportedPublisher;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;
import jakarta.servlet.Filter;
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

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공개 Endpoint와 기존 인증 기본값 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, PublisherControllerTest.SecurityTestConfiguration.class})
class PublisherControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private PublisherService publisherService;
    private MockMvc mockMvc;

    /** 보안 Filter 포함 MockMvc 구성 */
    @BeforeEach
    void setUp() {
        publisherService = mock(PublisherService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PublisherController(publisherService))
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 비로그인 지원 언론사 조회 검증 */
    @Test
    void getsSupportedPublishersWithoutAuthentication() throws Exception {
        when(publisherService.findSupportedPublishers()).thenReturn(List.of(
                new SupportedPublisher("연합뉴스", PublisherCategory.NEWS_AGENCY, PublisherAvailability.ACTIVE)
        ));

        mockMvc.perform(get("/api/publishers"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].name").value("연합뉴스"))
                .andExpect(jsonPath("$[0].category").value("NEWS_AGENCY"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    /** 지원 언론사 미등록 상태 응답 검증 */
    @Test
    void returnsEmptyArrayWhenNoPublisherIsAvailable() throws Exception {
        when(publisherService.findSupportedPublishers()).thenReturn(List.of());

        mockMvc.perform(get("/api/publishers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    /** 다른 요청의 인증 기본값 유지 검증 */
    @Test
    void keepsUnlistedRequestsProtected() throws Exception {
        mockMvc.perform(get("/api/private-check"))
                .andExpect(status().isUnauthorized());
    }

    /** 테스트용 Spring Security 기반 구성 */
    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        /** 테스트 사용자 저장소 구성 */
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
