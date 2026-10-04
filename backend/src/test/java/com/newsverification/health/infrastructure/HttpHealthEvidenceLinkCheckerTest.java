/* 건강 근거 링크 HTTP 상태 확인 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.article.application.HostResolver;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 외부 사이트 없이 운영 근거 링크 판정 경계 검증 */
class HttpHealthEvidenceLinkCheckerTest {

    /** 허용된 근거 링크의 성공 응답 판정 */
    @Test
    void reportsAvailableForSuccessfulAllowedEvidence() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        ArticleHttpClient client = (target, timeout, maxResponseBytes) -> {
            requestedUri.set(target.uri());
            assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
            assertThat(maxResponseBytes).isEqualTo(1_024);
            return new ArticleHttpResponse(200, "text/html", "", 0, null);
        };

        HealthEvidenceLinkChecker checker = new HttpHealthEvidenceLinkChecker(
                new ArticleUrlValidator(publicResolver()),
                client,
                Set.of("evidence.example"),
                Duration.ofSeconds(10),
                1_024,
                3
        );

        assertThat(checker.check(URI.create("https://evidence.example/article/1")))
                .isEqualTo(HealthEvidenceLinkChecker.Status.AVAILABLE);
        assertThat(requestedUri.get()).isEqualTo(URI.create("https://evidence.example/article/1"));
    }

    /** 허용된 Redirect 대상의 재검증과 최종 성공 판정 */
    @Test
    void followsAllowedRedirectAfterValidatingEveryTarget() throws Exception {
        var requestedUris = new ArrayList<URI>();
        var responses = new ArrayDeque<ArticleHttpResponse>();
        responses.add(response(302, "/article/final"));
        responses.add(response(204, null));
        ArticleHttpClient client = (target, timeout, maxResponseBytes) -> {
            requestedUris.add(target.uri());
            return responses.removeFirst();
        };

        HealthEvidenceLinkChecker checker = checker(publicResolver(), client);

        assertThat(checker.check(URI.create("https://evidence.example/article/start")))
                .isEqualTo(HealthEvidenceLinkChecker.Status.AVAILABLE);
        assertThat(requestedUris).containsExactly(
                URI.create("https://evidence.example/article/start"),
                URI.create("https://evidence.example/article/final")
        );
    }

    /** 허용하지 않은 Redirect 대상의 요청 전 차단 */
    @Test
    void reportsMissingWithoutRequestingDisallowedRedirectTarget() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ArticleHttpClient client = (target, timeout, maxResponseBytes) -> {
            requests.incrementAndGet();
            return response(302, "https://internal.example/private");
        };

        HealthEvidenceLinkChecker checker = checker(publicResolver(), client);

        assertThat(checker.check(URI.create("https://evidence.example/article/start")))
                .isEqualTo(HealthEvidenceLinkChecker.Status.MISSING);
        assertThat(requests).hasValue(1);
    }

    /** 사라진 근거 응답의 영구 누락 판정 */
    @Test
    void reportsMissingOnlyForNotFoundOrGoneResponses() throws Exception {
        assertThat(checker(publicResolver(), fixedResponse(404)).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.MISSING);
        assertThat(checker(publicResolver(), fixedResponse(410)).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.MISSING);
    }

    /** 서버 오류와 전송 실패의 일시 오류 판정 */
    @Test
    void reportsTemporaryFailureForServerOrTransportFailure() throws Exception {
        assertThat(checker(publicResolver(), fixedResponse(503)).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE);
        ArticleHttpClient failedClient = (target, timeout, maxResponseBytes) -> {
            throw new java.io.IOException("fixture transport failure");
        };
        assertThat(checker(publicResolver(), failedClient).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE);
    }

    /** DNS 일시 실패와 내부 주소의 분리 판정 */
    @Test
    void separatesDnsFailureFromUnsafeAddress() throws Exception {
        HostResolver failedDns = hostname -> {
            throw new java.net.UnknownHostException("fixture DNS failure");
        };
        assertThat(checker(failedDns, fixedResponse(200)).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE);

        HostResolver internalDns = hostname -> List.of(InetAddress.getLoopbackAddress());
        assertThat(checker(internalDns, fixedResponse(200)).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.MISSING);
    }

    /** Redirect 제한 초과의 영구 누락 판정 */
    @Test
    void reportsMissingWhenRedirectLimitIsExceeded() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        ArticleHttpClient client = (target, timeout, maxResponseBytes) -> {
            int number = requests.incrementAndGet();
            return response(302, "/article/redirect-" + number);
        };

        assertThat(checker(publicResolver(), client).check(evidenceUri()))
                .isEqualTo(HealthEvidenceLinkChecker.Status.MISSING);
        assertThat(requests).hasValue(4);
    }

    /** 기본 근거 링크 확인기 Fixture */
    private static HealthEvidenceLinkChecker checker(
            HostResolver resolver,
            ArticleHttpClient client
    ) {
        return new HttpHealthEvidenceLinkChecker(
                new ArticleUrlValidator(resolver),
                client,
                Set.of("evidence.example"),
                Duration.ofSeconds(10),
                1_024,
                3
        );
    }

    /** 고정 HTTP 상태 응답 Fixture */
    private static ArticleHttpClient fixedResponse(int statusCode) {
        return (target, timeout, maxResponseBytes) -> response(statusCode, null);
    }

    /** HTTP 응답 Fixture */
    private static ArticleHttpResponse response(int statusCode, String location) {
        return new ArticleHttpResponse(statusCode, "text/html", "", 0, location);
    }

    /** 허용 근거 링크 Fixture */
    private static URI evidenceUri() {
        return URI.create("https://evidence.example/article/1");
    }

    /** 공개 IP만 반환하는 테스트 DNS */
    private static HostResolver publicResolver() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});
        return hostname -> List.of(address);
    }
}
