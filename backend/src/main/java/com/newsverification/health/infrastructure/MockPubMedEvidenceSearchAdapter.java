/* PubMed 근거 검색 Mock */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.PubMedEvidenceSearchPort;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 실제 NCBI 호출 없는 PubMed 근거 Fixture Adapter */
public final class MockPubMedEvidenceSearchAdapter implements PubMedEvidenceSearchPort {

    private final Clock clock;

    /** 검색 Deadline 확인용 시각 구성 */
    public MockPubMedEvidenceSearchAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** 첫 번째 확인 주장 기반 고정 PubMed 근거 생성 */
    @Override
    public SearchResponse search(SearchRequest request) {
        if (!clock.instant().isBefore(request.deadlineAt())) {
            return SearchResponse.temporaryFailure();
        }
        String confirmedClaim = request.claims().get(0).originalText();
        return SearchResponse.completed(List.of(new Evidence(
                "00000001",
                confirmedClaim + " 관련 Mock 체계적 문헌고찰",
                HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW,
                LocalDate.of(2025, 1, 1),
                URI.create("https://pubmed.example/00000001/"),
                "외부 NCBI 호출 없이 사용하는 PubMed 근거 요약 Fixture"
        )));
    }
}
