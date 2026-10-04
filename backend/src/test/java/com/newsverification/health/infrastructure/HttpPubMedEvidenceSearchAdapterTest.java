/* 실제 PubMed Adapter 응답 변환 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 외부 사이트 없이 NCBI ESearch·EFetch 경계 검증 */
class HttpPubMedEvidenceSearchAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    /** 주장별 검색과 PMID 일괄 조회 결과 변환 */
    @Test
    void searchesEachClaimAndFetchesUniqueEvidenceInOneBatch() {
        var responses = new ArrayDeque<PubMedHttpClient.Response>();
        responses.add(response(200, searchJson("12345678", "23456789")));
        responses.add(response(200, searchJson("23456789", "34567890")));
        responses.add(response(200, fetchXml()));
        var requestedUris = new ArrayList<URI>();
        PubMedHttpClient client = (uri, timeout, maxResponseBytes) -> {
            requestedUris.add(uri);
            assertThat(timeout).isEqualTo(Duration.ofSeconds(10));
            assertThat(maxResponseBytes).isLessThanOrEqualTo(1_048_576);
            return responses.removeFirst();
        };
        var adapter = new HttpPubMedEvidenceSearchAdapter(
                client,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "developer@example.com",
                "",
                duration -> { }
        );

        PubMedEvidenceSearchPort.SearchResponse result = adapter.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(
                                claim("비타민 D 주장", "vitamin D respiratory infection"),
                                claim("독감 예방접종 주장", "influenza vaccination severe disease")
                        ),
                        2,
                        NOW.plusSeconds(60)
                )
        );

        assertThat(requestedUris).hasSize(3);
        assertThat(requestedUris.get(0).getRawQuery())
                .contains("db=pubmed", "retmax=2", "retmode=json", "tool=news_verification")
                .contains("email=developer%40example.com")
                .contains("term=vitamin+D+respiratory+infection")
                .contains("hasabstract");
        assertThat(requestedUris.get(1).getRawQuery())
                .contains("term=influenza+vaccination+severe+disease");
        assertThat(requestedUris.get(2).getRawQuery())
                .contains("id=12345678%2C23456789%2C34567890", "rettype=abstract", "retmode=xml");
        assertThat(result.status()).isEqualTo(PubMedEvidenceSearchPort.SearchStatus.COMPLETED);
        assertThat(result.evidences()).extracting(PubMedEvidenceSearchPort.Evidence::pmid)
                .containsExactly("12345678", "34567890");
        assertThat(result.evidences()).extracting(PubMedEvidenceSearchPort.Evidence::studyType)
                .containsExactly(
                        HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW,
                        HealthAnalysisResult.EvidenceStudyType.RANDOMIZED_TRIAL
                );
    }

    /** 검색 결과 없음의 정상 상태 분리 */
    @Test
    void reportsNoResultsWithoutFetchingDetails() {
        var requests = new AtomicInteger();
        PubMedHttpClient client = (uri, timeout, maxResponseBytes) -> {
            requests.incrementAndGet();
            return response(200, searchJson());
        };
        HttpPubMedEvidenceSearchAdapter adapter = adapter(client, "");

        PubMedEvidenceSearchPort.SearchResponse result = adapter.search(
                request(NOW.plusSeconds(30))
        );

        assertThat(result.status()).isEqualTo(PubMedEvidenceSearchPort.SearchStatus.NO_RESULTS);
        assertThat(result.evidences()).isEmpty();
        assertThat(requests).hasValue(1);
    }

    /** NCBI 오류와 잘못된 응답의 일시 실패 분리 */
    @Test
    void reportsTemporaryFailureForHttpOrInvalidResponse() {
        HttpPubMedEvidenceSearchAdapter failedAdapter = adapter(
                (uri, timeout, maxResponseBytes) -> response(429, "{}"),
                ""
        );
        HttpPubMedEvidenceSearchAdapter invalidAdapter = adapter(
                (uri, timeout, maxResponseBytes) -> response(200, "{}"),
                ""
        );

        assertThat(failedAdapter.search(request(NOW.plusSeconds(30))).status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
        assertThat(invalidAdapter.search(request(NOW.plusSeconds(30))).status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
    }

    /** Deadline 도달 뒤 외부 요청 차단 */
    @Test
    void stopsBeforeHttpRequestWhenDeadlineHasPassed() {
        var requests = new AtomicInteger();
        HttpPubMedEvidenceSearchAdapter adapter = adapter((uri, timeout, maxResponseBytes) -> {
            requests.incrementAndGet();
            return response(200, searchJson("12345678"));
        }, "");

        PubMedEvidenceSearchPort.SearchResponse result = adapter.search(request(NOW));

        assertThat(result.status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
        assertThat(requests).hasValue(0);
    }

    /** 선택 API Key의 NCBI 요청 포함 */
    @Test
    void includesOptionalApiKeyWithoutChangingSearchPolicy() {
        var requestedUri = new java.util.concurrent.atomic.AtomicReference<URI>();
        HttpPubMedEvidenceSearchAdapter adapter = adapter((uri, timeout, maxResponseBytes) -> {
            requestedUri.set(uri);
            return response(200, searchJson());
        }, "test-api-key");

        adapter.search(request(NOW.plusSeconds(30)));

        assertThat(requestedUri.get().getRawQuery()).contains("api_key=test-api-key");
    }

    /** 원문 주장과 영문 Query Fixture */
    private static PubMedEvidenceSearchPort.SearchClaim claim(String original, String query) {
        return new PubMedEvidenceSearchPort.SearchClaim(original, query);
    }

    /** 고정 ESearch JSON */
    private static String searchJson(String... ids) {
        if (ids.length == 0) {
            return "{\"esearchresult\":{\"idlist\":[]}}";
        }
        return "{\"esearchresult\":{\"idlist\":[\""
                + String.join("\",\"", ids)
                + "\"]}}";
    }

    /** 고정 EFetch XML */
    private static String fetchXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PubmedArticleSet>
                  <PubmedArticle>
                    <MedlineCitation>
                      <PMID>34567890</PMID>
                      <Article>
                        <ArticleTitle>Influenza randomized trial</ArticleTitle>
                        <Abstract><AbstractText Label="RESULTS">Influenza evidence summary.</AbstractText></Abstract>
                        <Journal><JournalIssue><PubDate><MedlineDate>2023 Oct-Dec</MedlineDate></PubDate></JournalIssue></Journal>
                        <PublicationTypeList><PublicationType>Randomized Controlled Trial</PublicationType></PublicationTypeList>
                      </Article>
                    </MedlineCitation>
                  </PubmedArticle>
                  <PubmedArticle>
                    <MedlineCitation>
                      <PMID>23456789</PMID>
                      <Article>
                        <ArticleTitle>Retracted evidence</ArticleTitle>
                        <Abstract><AbstractText>Excluded evidence.</AbstractText></Abstract>
                        <Journal><JournalIssue><PubDate><Year>2024</Year></PubDate></JournalIssue></Journal>
                        <PublicationTypeList><PublicationType>Retracted Publication</PublicationType></PublicationTypeList>
                      </Article>
                    </MedlineCitation>
                  </PubmedArticle>
                  <PubmedArticle>
                    <MedlineCitation>
                      <PMID>12345678</PMID>
                      <Article>
                        <ArticleTitle>Vitamin D systematic review</ArticleTitle>
                        <Abstract><AbstractText>Vitamin D evidence summary.</AbstractText></Abstract>
                        <Journal><JournalIssue><PubDate><Year>2025</Year><Month>Jan</Month><Day>2</Day></PubDate></JournalIssue></Journal>
                        <PublicationTypeList><PublicationType>Systematic Review</PublicationType></PublicationTypeList>
                      </Article>
                    </MedlineCitation>
                  </PubmedArticle>
                </PubmedArticleSet>
                """;
    }

    /** 고정 PubMed HTTP 응답 */
    private static PubMedHttpClient.Response response(int statusCode, String body) {
        return new PubMedHttpClient.Response(statusCode, body);
    }

    /** 단일 주장 검색 요청 Fixture */
    private static PubMedEvidenceSearchPort.SearchRequest request(Instant deadline) {
        return new PubMedEvidenceSearchPort.SearchRequest(
                List.of(claim("비타민 D 주장", "vitamin D respiratory infection")),
                5,
                deadline
        );
    }

    /** 고정 시각 Adapter Fixture */
    private static HttpPubMedEvidenceSearchAdapter adapter(
            PubMedHttpClient client,
            String apiKey
    ) {
        return new HttpPubMedEvidenceSearchAdapter(
                client,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "developer@example.com",
                apiKey,
                duration -> { }
        );
    }
}
