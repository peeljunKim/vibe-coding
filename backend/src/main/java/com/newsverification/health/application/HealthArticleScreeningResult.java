/* 건강 기사 분야 판별 결과 모델 */
package com.newsverification.health.application;

import com.newsverification.article.domain.ExtractedArticle;

/** 정제 기사와 후속 분석 결정 */
public record HealthArticleScreeningResult(
        ExtractedArticle article,
        HealthArticleTopicDecision decision
) {

    /** 후속 건강 분석 가능 여부 */
    public boolean canContinue() {
        return decision == HealthArticleTopicDecision.HEALTH_RELATED;
    }
}
