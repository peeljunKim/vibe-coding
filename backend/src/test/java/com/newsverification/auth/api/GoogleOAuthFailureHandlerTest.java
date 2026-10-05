/* Google OAuth 실패 분기 검증 */
package com.newsverification.auth.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

/** 사용자 취소와 일반 인증 실패 분리 */
class GoogleOAuthFailureHandlerTest {

    private final GoogleOAuthFailureHandler handler =
            new GoogleOAuthFailureHandler("http://localhost:5173");

    @Test
    void redirectsUserCancellationSeparately() throws Exception {
        MockHttpServletResponse response = handle("access_denied");

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=cancelled");
    }

    @Test
    void redirectsProviderFailureWithoutInternalDetail() throws Exception {
        MockHttpServletResponse response = handle("invalid_token_response");

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/login?oauth=failed");
    }

    private MockHttpServletResponse handle(String code) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("temporary", "value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error(code))
        );
        assertThat(request.getSession(false)).isNull();
        return response;
    }
}
