/* Local 건강 분석 결과 Mock */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthEvidenceLinkChecker;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import com.newsverification.health.application.PubMedEvidenceSearchService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Gemini 호출 없는 구조화 결과 생성 */
public class MockHealthAnalysisPort implements HealthAnalysisPort {

    private final Clock clock;
    private final PubMedEvidenceSearchService pubMedSearchService;

    /** 분석 시각 구성 */
    public MockHealthAnalysisPort(Clock clock) {
        this(
                clock,
                new PubMedEvidenceSearchService(
                        request -> PubMedEvidenceSearchPort.SearchResponse.noResults(),
                        sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
                )
        );
    }

    /** 분석 시각과 PubMed 검색 경계 구성 */
    public MockHealthAnalysisPort(
            Clock clock,
            PubMedEvidenceSearchService pubMedSearchService
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.pubMedSearchService = Objects.requireNonNull(pubMedSearchService);
    }

    /** 정제 기사 기반 근거 부족 Mock 결과 */
    @Override
    public HealthAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
        Objects.requireNonNull(article);
        Objects.requireNonNull(deadlineAt);
        if (!clock.instant().isBefore(deadlineAt)) {
            throw new IllegalStateException("Health analysis deadline exceeded");
        }
        PubMedEvidenceSearchPort.SearchResponse searchResponse = pubMedSearchService.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(new PubMedEvidenceSearchPort.SearchClaim(
                                article.title(),
                                "health claim evidence"
                        )),
                        1,
                        deadlineAt
                )
        );
        if (searchResponse.status() == PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE) {
            throw new IllegalStateException("PubMed evidence search temporarily unavailable");
        }
        List<HealthAnalysisResult.Evidence> evidences = searchResponse.evidences().stream()
                .map(MockHealthAnalysisPort::toHealthEvidence)
                .toList();
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        article.modifiedAt().orElse(null)
                ),
                clock.instant(),
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        article.title(),
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        evidences.isEmpty()
                                ? "Mock 분석에서는 확인된 PubMed 근거가 없습니다."
                                : "Mock PubMed 근거 구조를 확인한 제한 결과입니다.",
                        evidences
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                true
        );
    }

    /** PubMed 후보의 공통 건강 근거 변환 */
    private static HealthAnalysisResult.Evidence toHealthEvidence(
            PubMedEvidenceSearchPort.Evidence evidence
    ) {
        return new HealthAnalysisResult.Evidence(
                HealthAnalysisResult.EvidenceSourceKind.PUBMED,
                evidence.pmid(),
                evidence.studyType(),
                HealthAnalysisResult.EvidenceRelationType.CONTEXT,
                evidence.title(),
                "PubMed",
                evidence.publishedDate(),
                evidence.sourceUrl(),
                evidence.summary(),
                null
        );
    }
}
