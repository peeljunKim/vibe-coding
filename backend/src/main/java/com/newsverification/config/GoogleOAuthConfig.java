/* Google OAuth2 Redirect 실행 구성 */
package com.newsverification.config;

import com.newsverification.auth.api.GoogleOAuthFailureHandler;
import com.newsverification.auth.api.GoogleOAuthSuccessHandler;
import com.newsverification.auth.application.SocialLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Local 명시 활성화 시 Google Provider 등록 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.oauth.google.enabled", havingValue = "true")
public class GoogleOAuthConfig {

    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret,
            @Value("${GOOGLE_LOGIN_CALLBACK_URL:http://localhost:8080/oauth/google}") String callbackUrl
    ) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callbackUrl)
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    GoogleOAuthSuccessHandler googleOAuthSuccessHandler(
            SocialLoginService socialLoginService,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            @Value("${app.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        return new GoogleOAuthSuccessHandler(
                socialLoginService,
                authorizedClientRepository,
                frontendBaseUrl
        );
    }

    @Bean
    GoogleOAuthFailureHandler googleOAuthFailureHandler(
            @Value("${app.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        return new GoogleOAuthFailureHandler(frontendBaseUrl);
    }
}
