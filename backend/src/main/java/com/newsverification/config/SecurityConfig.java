package com.newsverification.config;

import com.newsverification.auth.api.SocialOAuthFailureHandler;
import com.newsverification.auth.api.SocialOAuthSuccessHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** SpringSecurity 기본 보안 구성 */
@Configuration
public class SecurityConfig {

    /** HTTP 요청 인증과 CSRF 정책 */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClients,
            ObjectProvider<SocialOAuthSuccessHandler> successHandlers,
            ObjectProvider<SocialOAuthFailureHandler> failureHandlers
    ) throws Exception {
        var csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");
        var csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/usage").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/publishers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/analyses/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/analyses/health/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/analyses/headline").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/analyses/headline/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shares/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/shares/headline").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/signup/social").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/signup/email-verification").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/signup/email-verification/resend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/recovery/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth2/authorization/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth2/authorization/naver").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth/google").permitAll()
                        .requestMatchers(HttpMethod.GET, "/oauth/naver").permitAll()
                        .requestMatchers("/api/admin/reports/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new AuthenticationProblemEntryPoint()));

        ClientRegistrationRepository registrationRepository = clientRegistrations.getIfAvailable();
        if (registrationRepository != null) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(registrationRepository)
                    .authorizedClientRepository(authorizedClients.getObject())
                    .redirectionEndpoint(redirection -> redirection.baseUri("/oauth/*"))
                    .successHandler(successHandlers.getObject())
                    .failureHandler(failureHandlers.getObject()));
        }
        return http.build();
    }
}
