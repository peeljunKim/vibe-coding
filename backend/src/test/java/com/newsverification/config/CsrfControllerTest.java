/* Frontend CSRF Cookie 발급 계약 검증 */
package com.newsverification.config;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비회원 CSRF 초기화 경계 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {SecurityConfig.class, CsrfControllerTest.SecurityTestConfiguration.class})
class CsrfControllerTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    /** 보안 Filter와 CSRF Controller 구성 */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CsrfController(), new CsrfProtectedTestController())
                .apply(springSecurity(springSecurityFilterChain))
                .build();
    }

    /** 비회원 요청의 CSRF Cookie 발급 */
    @Test
    void issuesCsrfCookieForGuestFrontend() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    /** Cookie 값을 Header로 전달하는 Frontend CSRF 왕복 */
    @Test
    void acceptsCsrfCookieValueAsRequestHeader() throws Exception {
        var csrfCookie = mockMvc.perform(get("/api/csrf"))
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/analyses/headline")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());
    }

    /** CSRF 검증 전용 공개 POST 경계 */
    @RestController
    static class CsrfProtectedTestController {

        /** 검증 성공 응답 */
        @PostMapping("/api/analyses/headline")
        void accept() {
        }
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
