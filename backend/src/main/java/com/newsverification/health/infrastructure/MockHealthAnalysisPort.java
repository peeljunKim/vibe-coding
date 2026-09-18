/* Local 건강 분석 결과 Mock */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Gemini 호출 없는 구조화 결과 생성 */
public class MockHealthAnalysisPort implements HealthAnalysisPort {

    private final Clock clock;

    /** 분석 시각 구성 */
    public MockHealthAnalysisPort(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** 정제 기사 기반 근거 부족 Mock 결과 */
    @Override
    public HealthAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
        Objects.requireNonNull(article);
        Objects.requireNonNull(deadlineAt);
        if (!clock.instant().isBefore(deadlineAt)) {
            throw new IllegalStateException("Health analysis deadline exceeded");
        }
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
                        "Mock 분석에서는 외부 근거 검색을 수행하지 않습니다.",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                true
        );
    }
}
