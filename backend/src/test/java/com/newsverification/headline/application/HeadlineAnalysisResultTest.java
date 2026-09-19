/* 기사 제목 분석 결과 불변식 검증 */
package com.newsverification.headline.application;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 문제 유형과 대체 제목 조합 검증 */
class HeadlineAnalysisResultTest {

    private static final HeadlineAnalysisResult.ArticleSummary ARTICLE =
            new HeadlineAnalysisResult.ArticleSummary(
                    URI.create("https://news.example/article"),
                    "기존 기사 제목",
                    "news.example",
                    OffsetDateTime.parse("2026-09-19T09:00:00+09:00"),
                    null
            );

    /** 문제 없음과 대체 제목의 동시 제공 거절 */
    @Test
    void rejectsAlternativeHeadlineForNoIssueResult() {
        assertThatThrownBy(() -> new HeadlineAnalysisResult(
                ARTICLE,
                Instant.parse("2026-09-19T01:00:00Z"),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE,
                        "제목과 본문의 핵심 내용이 일치합니다."
                )),
                "대체 제목"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 문제 발견 결과의 대체 제목 누락 거절 */
    @Test
    void requiresAlternativeHeadlineForDetectedIssue() {
        assertThatThrownBy(() -> new HeadlineAnalysisResult(
                ARTICLE,
                Instant.parse("2026-09-19T01:00:00Z"),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.EXAGGERATED,
                        "본문보다 효과를 단정적으로 표현했습니다."
                )),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    /** 같은 문제 유형의 중복 거절 */
    @Test
    void rejectsDuplicateIssueType() {
        assertThatThrownBy(() -> new HeadlineAnalysisResult(
                ARTICLE,
                Instant.parse("2026-09-19T01:00:00Z"),
                List.of(
                        new HeadlineAnalysisResult.Issue(
                                HeadlineAnalysisResult.IssueType.MISMATCH,
                                "첫 번째 설명"
                        ),
                        new HeadlineAnalysisResult.Issue(
                                HeadlineAnalysisResult.IssueType.MISMATCH,
                                "두 번째 설명"
                        )
                ),
                "중립적인 대체 제목"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
