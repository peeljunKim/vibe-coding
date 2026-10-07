/* Gemini 건강 분석 Adapter Fixture 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import com.newsverification.health.application.PubMedEvidenceSearchService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 외부 호출 없는 Gemini 요청·응답 경계 */
class HttpGeminiHealthAnalysisAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-06T01:00:00Z");
    private static final String MODEL = "gemini-3.7-flash";
    private static final String API_KEY = "fixture-api-key";

    /** 최소 기사 입력과 검증된 PubMed 근거의 구조화 결과 변환 */
    @Test
    void createsStructuredResultWithoutSendingArticleIdentityOrFullBody() {
        var responses = new ArrayDeque<GeminiHttpClient.Response>();
        responses.add(response(200, envelope("""
                {"claims":[{"order":1,"claim":"독감 예방접종은 고위험군의 중증 위험을 낮춘다",\
                "pubMedQuery":"influenza vaccination AND severe disease AND high risk"}]}
                """)));
        responses.add(response(200, envelope("""
                {"decisions":[{"order":1,"status":"SUPPORTED",\
                "reason":"체계적 문헌고찰 두 건이 중증 위험 감소를 뒷받침합니다.",\
                "evidences":[\
                {"pmid":"1001","relationType":"SUPPORTS","summary":"고위험군에서 중증 위험 감소",\
                "conflictDescription":null},\
                {"pmid":"1002","relationType":"SUPPORTS","summary":"예방접종 후 입원 위험 감소",\
                "conflictDescription":null}]}]}
                """)));
        var requests = new ArrayList<GeminiHttpClient.Request>();
        GeminiHttpClient client = request -> {
            requests.add(request);
            return responses.removeFirst();
        };
        var adapter = adapter(client, completedEvidence());
        String body = "건강 기사 앞부분 ".repeat(1_000) + "본문-후반-전송금지";

        HealthAnalysisResult result = adapter.analyze(
                article(body),
                NOW.plusSeconds(90)
        );

        assertThat(result.aiModelVersion()).isEqualTo(MODEL);
        assertThat(result.overallStatus()).isEqualTo(HealthAnalysisResult.OverallStatus.RELIABLE);
        assertThat(result.confirmationRate()).isEqualByComparingTo("100.00");
        assertThat(result.claims()).singleElement().satisfies(claim -> {
            assertThat(claim.status()).isEqualTo(HealthAnalysisResult.ClaimStatus.SUPPORTED);
            assertThat(claim.evidences()).hasSize(2);
        });
        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.apiKey()).isEqualTo(API_KEY);
            assertThat(request.body()).doesNotContain(API_KEY);
            assertThat(request.body()).doesNotContain("https://news.example/article/1");
            assertThat(request.timeout()).isPositive();
        });
        assertThat(requests.get(0).body())
                .contains("독감 예방접종 효과")
                .doesNotContain("본문-후반-전송금지");
        assertThat(requests.get(1).body())
                .contains("1001", "1002")
                .contains("\"responseJsonSchema\"")
                .contains("\"type\":[\"string\",\"null\"]")
                .doesNotContain("\"responseSchema\"")
                .doesNotContain("건강 기사 앞부분");
    }

    /** PubMed 근거 없음의 추가 Gemini 호출 없는 제한 결과 */
    @Test
    void returnsInsufficientResultWithoutDecisionCallWhenEvidenceIsMissing() {
        var requests = new ArrayList<GeminiHttpClient.Request>();
        GeminiHttpClient client = request -> {
            requests.add(request);
            return response(200, envelope("""
                    {"claims":[{"order":1,"claim":"독감 예방접종은 중증 위험을 낮춘다",\
                    "pubMedQuery":"influenza vaccination AND severe disease"}]}
                    """));
        };

        HealthAnalysisResult result = adapter(
                client,
                PubMedEvidenceSearchPort.SearchResponse.noResults()
        ).analyze(article("건강 기사 본문"), NOW.plusSeconds(90));

        assertThat(requests).hasSize(1);
        assertThat(result.limitedEvidence()).isTrue();
        assertThat(result.claims()).singleElement().satisfies(claim -> {
            assertThat(claim.status()).isEqualTo(HealthAnalysisResult.ClaimStatus.INSUFFICIENT);
            assertThat(claim.evidences()).isEmpty();
        });
    }

    /** 허용되지 않은 PMID 참조 응답 차단 */
    @Test
    void rejectsUnknownEvidenceReference() {
        var responses = new ArrayDeque<GeminiHttpClient.Response>();
        responses.add(response(200, envelope("""
                {"claims":[{"order":1,"claim":"독감 예방접종은 중증 위험을 낮춘다",\
                "pubMedQuery":"influenza vaccination AND severe disease"}]}
                """)));
        responses.add(response(200, envelope("""
                {"decisions":[{"order":1,"status":"SUPPORTED","reason":"근거 확인",\
                "evidences":[{"pmid":"9999","relationType":"SUPPORTS",\
                "summary":"허용되지 않은 근거","conflictDescription":null}]}]}
                """)));
        GeminiHttpClient client = request -> responses.removeFirst();

        assertThatThrownBy(() -> adapter(client, completedEvidence()).analyze(
                article("건강 기사 본문"),
                NOW.plusSeconds(90)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence reference");
    }

    /** 만료된 전체 Deadline의 HTTP 요청 전 차단 */
    @Test
    void rejectsExpiredDeadlineBeforeHttpCall() {
        var requests = new ArrayList<GeminiHttpClient.Request>();
        GeminiHttpClient client = request -> {
            requests.add(request);
            return response(200, "{}");
        };

        assertThatThrownBy(() -> adapter(client, completedEvidence()).analyze(
                article("건강 기사 본문"),
                NOW
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deadline");
        assertThat(requests).isEmpty();
    }

    /** Gemini Fixture Adapter */
    private HttpGeminiHealthAnalysisAdapter adapter(
            GeminiHttpClient client,
            PubMedEvidenceSearchPort.SearchResponse evidenceResponse
    ) {
        var searchService = new PubMedEvidenceSearchService(
                request -> evidenceResponse,
                sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        return new HttpGeminiHealthAnalysisAdapter(
                client,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                searchService,
                MODEL,
                API_KEY,
                "health-analysis-policy-v1",
                "evidence-allowlist-v1"
        );
    }

    /** 검증된 PubMed 근거 Fixture */
    private PubMedEvidenceSearchPort.SearchResponse completedEvidence() {
        return PubMedEvidenceSearchPort.SearchResponse.completed(List.of(
                evidence("1001", "독감 예방접종 체계적 문헌고찰"),
                evidence("1002", "고위험군 예방접종 메타분석")
        ));
    }

    /** PubMed 근거 Fixture */
    private PubMedEvidenceSearchPort.Evidence evidence(String pmid, String title) {
        return new PubMedEvidenceSearchPort.Evidence(
                pmid,
                title,
                HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW,
                LocalDate.of(2025, 1, 1),
                URI.create("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/"),
                "독감 예방접종의 중증 위험 감소 관련 초록 요약"
        );
    }

    /** 정제 기사 Fixture */
    private ExtractedArticle article(String body) {
        return new ExtractedArticle(
                URI.create("https://news.example/article/1"),
                "독감 예방접종 효과",
                body,
                OffsetDateTime.parse("2026-10-05T09:00:00+09:00"),
                Optional.empty()
        );
    }

    /** Gemini 응답 Fixture */
    private static GeminiHttpClient.Response response(int statusCode, String body) {
        return new GeminiHttpClient.Response(statusCode, body);
    }

    /** Gemini 후보 응답 Envelope */
    private static String envelope(String resultJson) {
        String escaped = resultJson.strip()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\""
                + escaped
                + "\"}]}}]}";
    }
}
