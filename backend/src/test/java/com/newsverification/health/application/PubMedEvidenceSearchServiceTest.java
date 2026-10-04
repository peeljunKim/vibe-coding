/* PubMed 근거 검색 결과 검증 */
package com.newsverification.health.application;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 검색 결과를 건강 분석용 근거로 제한하는 경계 검증 */
class PubMedEvidenceSearchServiceTest {

    private static final Instant DEADLINE = Instant.parse("2026-10-04T12:01:00Z");

    /** 허용된 PubMed 근거의 구조 유지 */
    @Test
    void returnsAllowedPubMedEvidenceForConfirmedClaim() {
        PubMedEvidenceSearchPort.Evidence evidence = evidence(
                "12345678",
                "https://pubmed.example/12345678/"
        );
        PubMedEvidenceSearchPort port = request ->
                PubMedEvidenceSearchPort.SearchResponse.completed(List.of(evidence));
        var service = new PubMedEvidenceSearchService(
                port,
                sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
        );

        PubMedEvidenceSearchPort.SearchResponse result = service.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(claim()),
                        2,
                        DEADLINE
                )
        );

        assertThat(result.status()).isEqualTo(PubMedEvidenceSearchPort.SearchStatus.COMPLETED);
        assertThat(result.evidences()).containsExactly(evidence);
    }

    /** PMID 중복 제거와 요청 결과 수 제한 */
    @Test
    void removesDuplicatePmidAndAppliesRequestLimit() {
        PubMedEvidenceSearchPort.Evidence first = evidence(
                "12345678",
                "https://pubmed.example/12345678/"
        );
        PubMedEvidenceSearchPort.Evidence duplicate = evidence(
                "12345678",
                "https://pubmed.example/12345678-duplicate/"
        );
        PubMedEvidenceSearchPort.Evidence second = evidence(
                "23456789",
                "https://pubmed.example/23456789/"
        );
        PubMedEvidenceSearchPort port = request ->
                PubMedEvidenceSearchPort.SearchResponse.completed(List.of(
                        first,
                        duplicate,
                        second
                ));
        var service = new PubMedEvidenceSearchService(
                port,
                sourceUrl -> HealthEvidenceLinkChecker.Status.AVAILABLE
        );

        PubMedEvidenceSearchPort.SearchResponse result = service.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(claim()),
                        1,
                        DEADLINE
                )
        );

        assertThat(result.evidences()).containsExactly(first);
    }

    /** 검색 결과 없음과 일시 오류 상태 보존 */
    @Test
    void preservesNoResultsAndTemporaryFailureWithoutLinkChecks() {
        var linkChecks = new java.util.concurrent.atomic.AtomicInteger();
        HealthEvidenceLinkChecker checker = sourceUrl -> {
            linkChecks.incrementAndGet();
            return HealthEvidenceLinkChecker.Status.AVAILABLE;
        };
        var request = new PubMedEvidenceSearchPort.SearchRequest(
                List.of(claim()),
                2,
                DEADLINE
        );

        var noResultsService = new PubMedEvidenceSearchService(
                ignored -> PubMedEvidenceSearchPort.SearchResponse.noResults(),
                checker
        );
        var failedService = new PubMedEvidenceSearchService(
                ignored -> PubMedEvidenceSearchPort.SearchResponse.temporaryFailure(),
                checker
        );

        assertThat(noResultsService.search(request).status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.NO_RESULTS);
        assertThat(failedService.search(request).status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
        assertThat(linkChecks).hasValue(0);
    }

    /** 근거 링크 일시 오류의 검색 실패 분리 */
    @Test
    void reportsTemporaryFailureWhenEvidenceLinkCannotBeVerified() {
        PubMedEvidenceSearchPort.Evidence evidence = evidence(
                "12345678",
                "https://pubmed.example/12345678/"
        );
        var service = new PubMedEvidenceSearchService(
                ignored -> PubMedEvidenceSearchPort.SearchResponse.completed(List.of(evidence)),
                sourceUrl -> HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE
        );

        PubMedEvidenceSearchPort.SearchResponse result = service.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(claim()),
                        2,
                        DEADLINE
                )
        );

        assertThat(result.status())
                .isEqualTo(PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE);
        assertThat(result.evidences()).isEmpty();
    }

    /** 누락 링크 제외 후 남은 PubMed 근거만 반환 */
    @Test
    void excludesMissingEvidenceLinks() {
        PubMedEvidenceSearchPort.Evidence missing = evidence(
                "12345678",
                "https://pubmed.example/missing/"
        );
        PubMedEvidenceSearchPort.Evidence available = evidence(
                "23456789",
                "https://pubmed.example/23456789/"
        );
        var service = new PubMedEvidenceSearchService(
                ignored -> PubMedEvidenceSearchPort.SearchResponse.completed(
                        List.of(missing, available)
                ),
                sourceUrl -> sourceUrl.getPath().contains("missing")
                        ? HealthEvidenceLinkChecker.Status.MISSING
                        : HealthEvidenceLinkChecker.Status.AVAILABLE
        );

        PubMedEvidenceSearchPort.SearchResponse result = service.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        List.of(claim()),
                        2,
                        DEADLINE
                )
        );

        assertThat(result.evidences()).containsExactly(available);
    }

    /** 확인 주장 수와 호출별 결과 제한 검증 */
    @Test
    void rejectsInvalidSearchRequestBoundaries() {
        assertThatThrownBy(() -> new PubMedEvidenceSearchPort.SearchRequest(
                List.of(claim(), claim(), claim(), claim()),
                2,
                DEADLINE
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PubMedEvidenceSearchPort.SearchClaim(
                " ",
                "vitamin D"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PubMedEvidenceSearchPort.SearchRequest(
                List.of(claim()),
                0,
                DEADLINE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 원문 주장과 정규화 영문 검색어 경계 */
    @Test
    void requiresEnglishQueryAndLimitsEachClaimToFiveCandidates() {
        PubMedEvidenceSearchPort.SearchClaim claim = new PubMedEvidenceSearchPort.SearchClaim(
                "비타민 D가 감기를 예방한다",
                "vitamin D common cold prevention"
        );

        assertThat(claim.originalText()).isEqualTo("비타민 D가 감기를 예방한다");
        assertThat(claim.pubMedQuery()).isEqualTo("vitamin D common cold prevention");
        assertThatThrownBy(() -> new PubMedEvidenceSearchPort.SearchClaim(
                "비타민 D가 감기를 예방한다",
                "비타민 D 감기 예방"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PubMedEvidenceSearchPort.SearchRequest(
                List.of(claim),
                6,
                DEADLINE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** PubMed 근거 Fixture */
    private static PubMedEvidenceSearchPort.Evidence evidence(String pmid, String sourceUrl) {
        return new PubMedEvidenceSearchPort.Evidence(
                pmid,
                "비타민 D와 호흡기 감염에 대한 체계적 문헌고찰",
                HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW,
                LocalDate.parse("2025-12-01"),
                URI.create(sourceUrl),
                "비타민 D와 호흡기 감염 연구 결과를 정리한 Mock 요약"
        );
    }

    /** 확인된 주장 Fixture */
    private static PubMedEvidenceSearchPort.SearchClaim claim() {
        return new PubMedEvidenceSearchPort.SearchClaim(
                "비타민 D가 감기를 예방한다",
                "vitamin D common cold prevention"
        );
    }
}
