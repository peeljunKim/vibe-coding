/* Kakao OAuth Redirect 보안 구성 검증 */
package com.newsverification.config;

import com.newsverification.auth.application.SocialLoginService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Kakao 시작 URL과 등록 Callback 일치 검증 */
@WebAppConfiguration
@SpringJUnitConfig(classes = {
        SecurityConfig.class,
        SocialOAuthConfig.class,
        KakaoOAuthSecurityTest.SecurityTestConfiguration.class
})
@TestPropertySource(properties = {
        "app.oauth.google.enabled=false",
        "app.oauth.naver.enabled=false",
        "app.oauth.kakao.enabled=true",
        "KAKAO_CLIENT_ID=placeholder",
        "KAKAO_CLIENT_SECRET=placeholder",
        "KAKAO_LOGIN_CALLBACK_URL=http://localhost:8080/oauth/kakao",
        "app.base-url=http://localhost:5173"
})
class KakaoOAuthSecurityTest {

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    /** Backend 시작 URL이 Kakao Authorization Redirect 생성 */
    @Test
    void startsKakaoAuthorizationWithConfiguredCallback() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Object())
                .apply(springSecurity(springSecurityFilterChain))
                .build();

        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("kauth.kakao.com")))
                .andExpect(header().string(
                        "Location",
                        containsString("redirect_uri=http://localhost:8080/oauth/kakao")
                ));
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        @Bean
        SocialLoginService socialLoginService() {
            return mock(SocialLoginService.class);
        }
    }
}
