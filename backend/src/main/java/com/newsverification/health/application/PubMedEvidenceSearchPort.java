/* PubMed 근거 검색 경계 */
package com.newsverification.health.application;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 확인된 건강 주장 기반 PubMed 검색 Port */
@FunctionalInterface
public interface PubMedEvidenceSearchPort {

    /** 남은 Deadline 안의 PubMed 근거 검색 */
    SearchResponse search(SearchRequest request);

    /** 확인된 주장과 주장별 후보 제한 */
    record SearchRequest(
            List<SearchClaim> claims,
            int maxResultsPerClaim,
            Instant deadlineAt
    ) {
        public SearchRequest {
            Objects.requireNonNull(claims);
            Objects.requireNonNull(deadlineAt);
            claims = List.copyOf(claims);
            if (claims.isEmpty()
                    || claims.size() > 3
                    || claims.stream().anyMatch(Objects::isNull)
                    || maxResultsPerClaim < 1
                    || maxResultsPerClaim > 5) {
                throw new IllegalArgumentException("Invalid PubMed search request");
            }
        }
    }

    /** 사용자 표시 주장과 PubMed용 영문 검색어 */
    record SearchClaim(
            String originalText,
            String pubMedQuery
    ) {
        public SearchClaim {
            if (originalText == null || originalText.isBlank()
                    || pubMedQuery == null || pubMedQuery.isBlank()
                    || pubMedQuery.length() > 300
                    || pubMedQuery.chars().anyMatch(character -> character > 127)) {
                throw new IllegalArgumentException("Invalid PubMed search claim");
            }
        }
    }

    /** 검색 상태와 검증 전 후보 */
    record SearchResponse(
            SearchStatus status,
            List<Evidence> evidences
    ) {
        public SearchResponse {
            Objects.requireNonNull(status);
            Objects.requireNonNull(evidences);
            evidences = List.copyOf(evidences);
        }

        /** 검색 완료 응답 */
        public static SearchResponse completed(List<Evidence> evidences) {
            return new SearchResponse(SearchStatus.COMPLETED, evidences);
        }

        /** 검색 결과 없음 응답 */
        public static SearchResponse noResults() {
            return new SearchResponse(SearchStatus.NO_RESULTS, List.of());
        }

        /** 검색 서비스 일시 오류 응답 */
        public static SearchResponse temporaryFailure() {
            return new SearchResponse(SearchStatus.TEMPORARY_FAILURE, List.of());
        }
    }

    /** PubMed 최소 근거 정보 */
    record Evidence(
            String pmid,
            String title,
            HealthAnalysisResult.EvidenceStudyType studyType,
            LocalDate publishedDate,
            URI sourceUrl,
            String summary
    ) {
        public Evidence {
            Objects.requireNonNull(studyType);
            Objects.requireNonNull(publishedDate);
            Objects.requireNonNull(sourceUrl);
            if (pmid == null || pmid.isBlank()
                    || title == null || title.isBlank()
                    || summary == null || summary.isBlank()) {
                throw new IllegalArgumentException("Invalid PubMed evidence");
            }
        }
    }

    /** 검색 실행 결과 */
    enum SearchStatus {
        COMPLETED,
        NO_RESULTS,
        TEMPORARY_FAILURE
    }
}
