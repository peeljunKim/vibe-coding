/* Local 기사 제목 분석 결과 Mock */
package com.newsverification.headline.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.headline.application.HeadlineAnalysisPort;
import com.newsverification.headline.application.HeadlineAnalysisResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 외부 AI 호출 없는 제목·본문 비교 결과 생성 */
public class MockHeadlineAnalysisPort implements HeadlineAnalysisPort {

    private final Clock clock;

    /** 분석 시각 구성 */
    public MockHeadlineAnalysisPort(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    /** 정제 기사 기반 문제 없음 Mock 결과 */
    @Override
    public HeadlineAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
        Objects.requireNonNull(article);
        Objects.requireNonNull(deadlineAt);
        if (!clock.instant().isBefore(deadlineAt)) {
            throw new IllegalStateException("Headline analysis deadline exceeded");
        }
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        article.sourceUrl(), article.title(), article.sourceUrl().getHost(),
                        article.publishedAt(), article.modifiedAt().orElse(null)
                ),
                clock.instant(),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE,
                        "Mock 분석에서는 제목과 본문의 핵심 내용이 일치하는 것으로 처리합니다."
                )),
                null
        );
    }
}
