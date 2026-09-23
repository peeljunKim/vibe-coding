/* Native MySQL과 Docker Redis 로그인 통합 검증 */
package com.newsverification.auth.api;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 영속 계정과 Redis Session 수명주기 검증 */
@SpringBootTest(
        classes = NewsVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "HEALTH_ANALYSIS_PROVIDER=mock",
                "LOOKUP_HMAC_KEY=integration-test-only-login-session-key"
        }
)
class AuthFullStackIT {

    private static final String TEST_PASSWORD = "Integration-Password23!";

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.session.data.redis.namespace}")
    private String sessionNamespace;

    private Long createdUserId;
    private String createdSessionId;
    private String createdSessionKey;

    /** 로그인부터 Session 조회와 로그아웃까지 실제 저장소 검증 */
    @Test
    void authenticatesAndInvalidatesRedisSession() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        String username = "login2" + suffix.substring(Math.max(0, suffix.length() - 8));
        UserAccount account = new UserAccount(
                username,
                passwordEncoder.encode(TEST_PASSWORD),
                username + "@example.com",
                "010" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L)),
                Instant.now().minusSeconds(60)
        );
        account.activate(Instant.now().minusSeconds(30));
        createdUserId = repository.saveAndFlush(account).id();

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrfToken = issueCsrfToken(client, cookies);

        HttpResponse<String> loginResponse = client.send(
                request("/api/auth/login")
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                                new AuthController.LoginRequest(username, TEST_PASSWORD, false)
                        )))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        JsonNode loginBody = objectMapper.readTree(loginResponse.body());
        assertThat(loginBody.path("authenticated").asBoolean()).isTrue();
        assertThat(loginBody.path("userId").asText()).isEqualTo(Long.toString(createdUserId));

        createdSessionId = currentSessionId(cookies);
        createdSessionKey = sessionNamespace + ":sessions:" + createdSessionId;
        assertThat(sessionRepository.getClass().getName()).contains("RedisIndexedSessionRepository");
        assertThat(sessionRepository.findById(createdSessionId)).isNotNull();
        assertThat(redisTemplate.hasKey(createdSessionKey)).isTrue();

        HttpResponse<String> sessionResponse = client.send(
                request("/api/auth/session").GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(sessionResponse.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(sessionResponse.body()).path("authenticated").asBoolean()).isTrue();

        csrfToken = issueCsrfToken(client, cookies);
        HttpResponse<Void> logoutResponse = client.send(
                request("/api/auth/logout")
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.discarding()
        );
        assertThat(logoutResponse.statusCode()).isEqualTo(204);
        assertThat(sessionRepository.findById(createdSessionId)).isNull();

        HttpResponse<String> anonymousResponse = client.send(
                request("/api/auth/session").GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(objectMapper.readTree(anonymousResponse.body()).path("authenticated").asBoolean()).isFalse();
    }

    @AfterEach
    void cleanUp() {
        if (createdSessionId != null) {
            sessionRepository.deleteById(createdSessionId);
        }
        if (createdUserId != null) {
            repository.deleteById(createdUserId);
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private String issueCsrfToken(HttpClient client, CookieManager cookies) throws Exception {
        HttpResponse<Void> response = client.send(
                request("/api/csrf").GET().build(),
                HttpResponse.BodyHandlers.discarding()
        );
        assertThat(response.statusCode()).isEqualTo(204);
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .filter(cookie -> !cookie.hasExpired())
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }

    private String currentSessionId(CookieManager cookies) {
        String encodedSessionId = cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "NEWS_VERIFICATION_SESSION".equals(cookie.getName()))
                .filter(cookie -> !cookie.hasExpired())
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
        return new String(Base64.getDecoder().decode(encodedSessionId), StandardCharsets.UTF_8);
    }
}
