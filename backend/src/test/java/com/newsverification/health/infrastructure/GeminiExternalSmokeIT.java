/* 승인된 Gemini 외부 연결 수동 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import com.newsverification.health.application.PubMedEvidenceSearchService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Gemini 두 단계 요청과 Mock PubMed 근거 연결 확인 */
class GeminiExternalSmokeIT {

    private static final String MODEL = "gemini-3.8-flash";
    private static final int MAX_CALLS = 2;

    /** 주장 추출 1회와 근거 판정 1회 제한 검증 */
    @Test
    void analyzesSyntheticHealthArticleWithExactlyTwoCalls() {
        String apiKey = requireEnvironment("AI_API_KEY");
        Clock clock = Clock.systemUTC();
        var client = new LimitedGeminiHttpClient(MAX_CALLS);
        var evidenceSearch = new PubMedEvidenceSearchService(
                new MockPubMedEvidenceSearchAdapter(clock),
                sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        var adapter = new HttpGeminiHealthAnalysisAdapter(
                client,
                new ObjectMapper(),
                clock,
                evidenceSearch,
                MODEL,
                apiKey,
                "health-analysis-policy-v1",
                "evidence-allowlist-v1"
        );
        var article = new ExtractedArticle(
                URI.create("https://news.example/health/smoke"),
                "규칙적인 걷기와 심혈관 건강",
                "규칙적인 걷기는 심혈관 건강에 도움이 될 수 있다는 내용을 설명합니다.",
                OffsetDateTime.of(2026, 10, 12, 12, 0, 0, 0, ZoneOffset.UTC),
                Optional.empty()
        );

        HealthAnalysisResult result = adapter.analyze(
                article,
                Instant.now(clock).plusSeconds(90)
        );

        assertThat(client.callCount()).isEqualTo(MAX_CALLS);
        assertThat(result.aiModelVersion()).isEqualTo(MODEL);
        assertThat(result.claims()).isNotEmpty();
    }

    /** 필수 Process 환경 변수 확인 */
    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required");
        }
        return value;
    }

    /** 실제 HTTP 요청 횟수 상한 */
    private static final class LimitedGeminiHttpClient implements GeminiHttpClient {

        private final int maxCalls;
        private final GeminiHttpClient delegate = new JdkGeminiHttpClient();
        private int callCount;

        private LimitedGeminiHttpClient(int maxCalls) {
            this.maxCalls = maxCalls;
        }

        @Override
        public Response post(Request request) throws IOException, InterruptedException {
            if (callCount >= maxCalls) {
                throw new IllegalStateException("Gemini smoke call limit exceeded");
            }
            callCount++;
            return delegate.post(request);
        }

        private int callCount() {
            return callCount;
        }
    }
}
