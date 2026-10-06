/* 소셜 OAuth2 Redirect 실행 구성 */
package com.newsverification.config;

import com.newsverification.auth.api.SocialOAuthFailureHandler;
import com.newsverification.auth.api.SocialOAuthSuccessHandler;
import com.newsverification.auth.application.SocialLoginService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import java.util.ArrayList;
import java.util.List;

/** Local 명시 활성화된 OAuth Provider 등록 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "${app.oauth.google.enabled:false} or "
                + "${app.oauth.naver.enabled:false} or "
                + "${app.oauth.kakao.enabled:false}"
)
public class SocialOAuthConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.oauth.google.enabled:false}") boolean googleEnabled,
            @Value("${GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${GOOGLE_LOGIN_CALLBACK_URL:http://localhost:8080/oauth/google}") String googleCallbackUrl,
            @Value("${app.oauth.naver.enabled:false}") boolean naverEnabled,
            @Value("${NAVER_CLIENT_ID:}") String naverClientId,
            @Value("${NAVER_CLIENT_SECRET:}") String naverClientSecret,
            @Value("${NAVER_LOGIN_CALLBACK_URL:http://localhost:8080/oauth/naver}") String naverCallbackUrl,
            @Value("${app.oauth.kakao.enabled:false}") boolean kakaoEnabled,
            @Value("${KAKAO_CLIENT_ID:}") String kakaoClientId,
            @Value("${KAKAO_CLIENT_SECRET:}") String kakaoClientSecret,
            @Value("${KAKAO_LOGIN_CALLBACK_URL:http://localhost:8080/oauth/kakao}") String kakaoCallbackUrl
    ) {
        List<ClientRegistration> registrations = new ArrayList<>(3);
        if (googleEnabled) {
            registrations.add(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .redirectUri(googleCallbackUrl)
                    .scope("openid", "profile", "email")
                    .build());
        }
        if (naverEnabled) {
            registrations.add(naverRegistration(
                    naverClientId,
                    naverClientSecret,
                    naverCallbackUrl
            ));
        }
        if (kakaoEnabled) {
            registrations.add(kakaoRegistration(
                    kakaoClientId,
                    kakaoClientSecret,
                    kakaoCallbackUrl
            ));
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration naverRegistration(
            String clientId,
            String clientSecret,
            String callbackUrl
    ) {
        return ClientRegistration.withRegistrationId("naver")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(callbackUrl)
                .scope("email")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }

    private ClientRegistration kakaoRegistration(
            String clientId,
            String clientSecret,
            String callbackUrl
    ) {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(callbackUrl)
                .scope("account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    @Bean
    OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    @Bean
    SocialOAuthSuccessHandler socialOAuthSuccessHandler(
            SocialLoginService socialLoginService,
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            @Value("${app.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        return new SocialOAuthSuccessHandler(
                socialLoginService,
                authorizedClientRepository,
                frontendBaseUrl
        );
    }

    @Bean
    SocialOAuthFailureHandler socialOAuthFailureHandler(
            @Value("${app.base-url:http://localhost:5173}") String frontendBaseUrl
    ) {
        return new SocialOAuthFailureHandler(frontendBaseUrl);
    }
}
