/* 건강 분석 Local Mock 동작 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthArticleTopicDecision;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 호출 없는 분야 판별과 구조화 결과 */
class MockHealthAnalysisAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");

    /** 건강·일반·불명확 기사 판별 */
    @Test
    void classifiesFixtureArticlesDeterministically() {
        var classifier = new MockHealthArticleTopicClassifier();

        assertThat(classifier.classify(article(
                "독감 예방접종 대상 안내",
                "질병관리청이 예방접종 시기를 안내했습니다."
        ))).isEqualTo(HealthArticleTopicDecision.HEALTH_RELATED);
        assertThat(classifier.classify(article(
                "지역 축제 개막",
                "행사장 주변 교통 통제 시간이 발표됐습니다."
        ))).isEqualTo(HealthArticleTopicDecision.NOT_HEALTH_RELATED);
        assertThat(classifier.classify(article(
                "생활 습관 변화",
                "전문가 의견을 더 살펴봐야 합니다."
        ))).isEqualTo(HealthArticleTopicDecision.UNCERTAIN);
    }

    /** 정제 기사 기반 근거 부족 결과 생성 */
    @Test
    void createsStructuredMockResultBeforeDeadline() {
        var port = new MockHealthAnalysisPort(Clock.fixed(NOW, ZoneOffset.UTC));

        HealthAnalysisResult result = port.analyze(
                article("독감 예방접종 대상 안내", "건강 기사 본문"),
                NOW.plusSeconds(30)
        );

        assertThat(result.analyzedAt()).isEqualTo(NOW);
        assertThat(result.overallStatus()).isEqualTo(HealthAnalysisResult.OverallStatus.CAUTION);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.limitedEvidence()).isTrue();
    }

    /** 기한 이후 Mock 분석 차단 */
    @Test
    void rejectsAnalysisAtDeadline() {
        var port = new MockHealthAnalysisPort(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> port.analyze(
                article("독감 예방접종 대상 안내", "건강 기사 본문"),
                NOW
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Health analysis deadline exceeded");
    }

    /** 외부 호출 없는 PubMed 근거 Fixture 생성 */
    @Test
    void createsPubMedFixtureForConfirmedClaim() {
        var adapter = new MockPubMedEvidenceSearchAdapter(Clock.fixed(NOW, ZoneOffset.UTC));

        PubMedEvidenceSearchPort.SearchResponse result = adapter.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(new PubMedEvidenceSearchPort.SearchClaim(
                                "독감 예방접종은 고위험군의 중증 위험을 낮춘다",
                                "influenza vaccination severe disease high risk"
                        )),
                        2,
                        NOW.plusSeconds(30)
                )
        );

        assertThat(result.status()).isEqualTo(PubMedEvidenceSearchPort.SearchStatus.COMPLETED);
        assertThat(result.evidences()).singleElement().satisfies(evidence -> {
            assertThat(evidence.pmid()).isEqualTo("00000001");
            assertThat(evidence.title()).contains("독감 예방접종");
            assertThat(evidence.sourceUrl()).isEqualTo(
                    URI.create("https://pubmed.example/00000001/")
            );
        });
    }

    /** Deadline 도달 뒤 Mock 검색 중단 */
    @Test
    void reportsTemporaryFailureAtPubMedDeadline() {
        var adapter = new MockPubMedEvidenceSearchAdapter(Clock.fixed(NOW, ZoneOffset.UTC));

        PubMedEvidenceSearchPort.SearchResponse result = adapter.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(new PubMedEvidenceSearchPort.SearchClaim(
                                "독감 예방접종은 고위험군의 중증 위험을 낮춘다",
                                "influenza vaccination severe disease high risk"
                        )),
                        2,
                        NOW
                )
        );

        assertThat(result.status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
        assertThat(result.evidences()).isEmpty();
    }

    /** 확인된 핵심 주장 기반 PubMed Mock 근거 연결 */
    @Test
    void connectsPubMedEvidenceToStructuredHealthResult() {
        var receivedRequest = new AtomicReference<PubMedEvidenceSearchPort.SearchRequest>();
        PubMedEvidenceSearchPort.Evidence evidence = new PubMedEvidenceSearchPort.Evidence(
                "00000001",
                "독감 예방접종 관련 Mock 체계적 문헌고찰",
                HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW,
                java.time.LocalDate.of(2025, 1, 1),
                URI.create("https://pubmed.example/00000001/"),
                "외부 NCBI 호출 없는 PubMed 근거 요약 Fixture"
        );
        var searchService = new com.newsverification.health.application.PubMedEvidenceSearchService(
                request -> {
                    receivedRequest.set(request);
                    return PubMedEvidenceSearchPort.SearchResponse.completed(List.of(evidence));
                },
                sourceUrl -> com.newsverification.health.application.HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        var port = new MockHealthAnalysisPort(
                Clock.fixed(NOW, ZoneOffset.UTC),
                searchService
        );

        HealthAnalysisResult result = port.analyze(
                article("독감 예방접종 대상 안내", "건강 기사 본문"),
                NOW.plusSeconds(30)
        );

        assertThat(receivedRequest.get().claims())
                .extracting(PubMedEvidenceSearchPort.SearchClaim::originalText)
                .containsExactly("독감 예방접종 대상 안내");
        assertThat(result.claims()).singleElement().satisfies(claim ->
                assertThat(claim.evidences()).singleElement().satisfies(connected -> {
                    assertThat(connected.sourceKind())
                            .isEqualTo(HealthAnalysisResult.EvidenceSourceKind.PUBMED);
                    assertThat(connected.sourceIdentifier()).isEqualTo("00000001");
                    assertThat(connected.sourceUrl())
                            .isEqualTo(URI.create("https://pubmed.example/00000001/"));
                })
        );
    }

    /** 고정 정제 기사 */
    private ExtractedArticle article(String title, String body) {
        return new ExtractedArticle(
                URI.create("https://news.example/article/1"),
                title,
                body,
                OffsetDateTime.parse("2026-08-14T09:30:00+09:00"),
                Optional.empty()
        );
    }
}
