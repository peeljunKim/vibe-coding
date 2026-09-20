/* 기사 제목 분석 결과 모델 */
package com.newsverification.headline.application;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 제목·본문 비교의 구조화 결과 */
public record HeadlineAnalysisResult(
        ArticleSummary article,
        Instant analyzedAt,
        List<Issue> issues,
        String alternativeHeadline
) {

    /** 결과 필드와 판정 조합 검증 */
    public HeadlineAnalysisResult {
        Objects.requireNonNull(article);
        Objects.requireNonNull(analyzedAt);
        if (issues == null || issues.isEmpty() || issues.size() > 3) {
            throw new IllegalArgumentException("Headline issues must contain between one and three items");
        }
        issues = List.copyOf(issues);
        if (new HashSet<>(issues.stream().map(Issue::type).toList()).size() != issues.size()) {
            throw new IllegalArgumentException("Headline issue types must be unique");
        }

        boolean noIssue = issues.stream().anyMatch(issue -> issue.type() == IssueType.NO_ISSUE);
        if (noIssue && (issues.size() != 1 || hasText(alternativeHeadline))) {
            throw new IllegalArgumentException("No-issue result cannot contain other issues or an alternative headline");
        }
        if (!noIssue && !hasText(alternativeHeadline)) {
            throw new IllegalArgumentException("Detected headline issue requires an alternative headline");
        }
        alternativeHeadline = hasText(alternativeHeadline) ? alternativeHeadline.trim() : null;
    }

    /** 공백 제외 문자열 존재 확인 */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 분석 대상 기사 요약 */
    public record ArticleSummary(
            URI url,
            String title,
            String publisher,
            OffsetDateTime publishedAt,
            OffsetDateTime modifiedAt
    ) {

        /** 기사 요약 필수값 검증 */
        public ArticleSummary {
            Objects.requireNonNull(url);
            if (!hasText(title) || !hasText(publisher)) {
                throw new IllegalArgumentException("Article title and publisher are required");
            }
            Objects.requireNonNull(publishedAt);
            title = title.trim();
            publisher = publisher.trim();
        }
    }

    /** 제목 문제 유형과 설명 */
    public record Issue(IssueType type, String explanation) {

        /** 문제 유형과 설명 필수값 검증 */
        public Issue {
            Objects.requireNonNull(type);
            if (!hasText(explanation)) {
                throw new IllegalArgumentException("Headline issue explanation is required");
            }
            explanation = explanation.trim();
        }
    }

    /** 제목·본문 비교 판정 */
    public enum IssueType {
        NO_ISSUE,
        EXAGGERATED,
        OMITS_CONTEXT,
        MISMATCH
    }
}
