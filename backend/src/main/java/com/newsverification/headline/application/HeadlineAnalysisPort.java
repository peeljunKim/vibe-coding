/* 기사 제목 분석 Port */
package com.newsverification.headline.application;

import com.newsverification.article.domain.ExtractedArticle;

import java.time.Instant;

/** 정제된 기사 제목과 본문 비교 경계 */
@FunctionalInterface
public interface HeadlineAnalysisPort {

    /** 남은 Deadline 안의 제목 분석 */
    HeadlineAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt);
}
