/* 후속 건강 분석 Port */
package com.newsverification.health.application;

import com.newsverification.article.domain.ExtractedArticle;

import java.time.Instant;

/** 정제된 건강 기사 분석 시작 경계 */
@FunctionalInterface
public interface HealthAnalysisPort {

    /** 남은 Deadline 안의 후속 건강 분석 */
    HealthAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt);
}
