/* 건강 근거 링크 재확인과 제한 결과 검증 */
package com.newsverification.health.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/** 일시 오류 재확인과 깨진 근거 의존 주장 제거 검증 */
class HealthEvidenceLinkValidationServiceTest {

    /** 일시 오류 뒤 정상 응답의 Cache 유지 */
    @Test
    void retriesTemporaryFailureOnceBeforeKeepingCachedResult() {
        URI sourceUrl = URI.create("https://evidence.example/guideline");
        var checker = new StubChecker();
        checker.respond(
                sourceUrl,
                HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE,
                HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        var service = new HealthEvidenceLinkValidationService(checker);

        HealthEvidenceLinkValidation result = service.validate(resultWithEvidence(sourceUrl));

        assertThat(result.status()).isEqualTo(HealthEvidenceLinkValidation.Status.AVAILABLE);
        assertThat(checker.calls(sourceUrl)).isEqualTo(2);
        assertThat(result.limitedResult()).isEmpty();
    }

    /** 반복된 일시 오류의 Cache 보존 판단 */
    @Test
    void reportsTemporaryFailureWithoutInvalidatingCachedResult() {
        URI sourceUrl = URI.create("https://evidence.example/temporarily-unavailable");
        var checker = new StubChecker();
        checker.respond(
                sourceUrl,
                HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE,
                HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE
        );
        var service = new HealthEvidenceLinkValidationService(checker);

        HealthEvidenceLinkValidation result = service.validate(resultWithEvidence(sourceUrl));

        assertThat(result.status())
                .isEqualTo(HealthEvidenceLinkValidation.Status.TEMPORARY_FAILURE);
        assertThat(checker.calls(sourceUrl)).isEqualTo(2);
        assertThat(result.limitedResult()).isEmpty();
    }

    /** 깨진 근거 의존 주장 제거와 확인률 재계산 */
    @Test
    void removesClaimsDependingOnMissingEvidenceAndRecalculatesJudgment() {
        URI availableUrl = URI.create("https://evidence.example/available");
        URI missingUrl = URI.create("https://evidence.example/missing");
        var checker = new StubChecker();
        checker.respond(availableUrl, HealthEvidenceLinkChecker.Status.AVAILABLE);
        checker.respond(missingUrl, HealthEvidenceLinkChecker.Status.MISSING);
        var service = new HealthEvidenceLinkValidationService(checker);

        HealthEvidenceLinkValidation validation = service.validate(resultWithTwoClaims(
                availableUrl,
                missingUrl
        ));

        assertThat(validation.status()).isEqualTo(HealthEvidenceLinkValidation.Status.MISSING);
        HealthAnalysisResult limited = validation.limitedResult().orElseThrow();
        assertThat(limited.claims()).extracting(HealthAnalysisResult.Claim::order)
                .containsExactly(1);
        assertThat(limited.confirmedClaimCount()).isEqualTo(1);
        assertThat(limited.totalClaimCount()).isEqualTo(1);
        assertThat(limited.confirmationRate()).isEqualByComparingTo("100.00");
        assertThat(limited.overallStatus()).isEqualTo(HealthAnalysisResult.OverallStatus.RELIABLE);
        assertThat(limited.limitedEvidence()).isTrue();
    }

    /** 근거 URL별 상태 응답 Stub */
    private static final class StubChecker implements HealthEvidenceLinkChecker {

        private final Map<URI, Queue<Status>> responses = new HashMap<>();
        private final Map<URI, Integer> calls = new HashMap<>();

        private void respond(URI sourceUrl, Status... statuses) {
            responses.put(sourceUrl, new ArrayDeque<>(List.of(statuses)));
        }

        private int calls(URI sourceUrl) {
            return calls.getOrDefault(sourceUrl, 0);
        }

        @Override
        public Status check(URI sourceUrl) {
            calls.merge(sourceUrl, 1, Integer::sum);
            Queue<Status> queued = responses.get(sourceUrl);
            if (queued == null || queued.isEmpty()) {
                throw new IllegalStateException("Missing test response");
            }
            return queued.remove();
        }
    }

    /** 단일 근거 결과 Fixture */
    private HealthAnalysisResult resultWithEvidence(URI sourceUrl) {
        return resultWithClaims(List.of(claim(1, HealthAnalysisResult.ClaimStatus.SUPPORTED, sourceUrl)));
    }

    /** 정상·깨진 근거 결과 Fixture */
    private HealthAnalysisResult resultWithTwoClaims(URI availableUrl, URI missingUrl) {
        return resultWithClaims(List.of(
                claim(1, HealthAnalysisResult.ClaimStatus.SUPPORTED, availableUrl),
                claim(2, HealthAnalysisResult.ClaimStatus.CONTRADICTED, missingUrl)
        ));
    }

    /** 주장 목록 기반 건강 결과 Fixture */
    private HealthAnalysisResult resultWithClaims(List<HealthAnalysisResult.Claim> claims) {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"),
                        "건강 기사",
                        "테스트 언론사",
                        OffsetDateTime.parse("2026-10-01T09:00:00+09:00"),
                        null
                ),
                Instant.parse("2026-10-01T00:00:00Z"),
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.valueOf(50).setScale(2),
                1,
                claims.size(),
                claims,
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                false
        );
    }

    /** 단일 근거 주장 Fixture */
    private HealthAnalysisResult.Claim claim(
            int order,
            HealthAnalysisResult.ClaimStatus status,
            URI sourceUrl
    ) {
        return new HealthAnalysisResult.Claim(
                order,
                "검증할 주장 " + order,
                status,
                "판정 이유",
                List.of(new HealthAnalysisResult.Evidence(
                        HealthAnalysisResult.EvidenceSourceKind.OFFICIAL,
                        "source-" + order,
                        HealthAnalysisResult.EvidenceStudyType.GUIDELINE,
                        HealthAnalysisResult.EvidenceRelationType.SUPPORTS,
                        "근거 자료",
                        "공식 기관",
                        LocalDate.parse("2026-09-30"),
                        sourceUrl,
                        "근거 요약",
                        null
                ))
        );
    }
}
