/* 건강 기사 분야 판별 Port */
package com.newsverification.health.application;

import com.newsverification.article.domain.ExtractedArticle;

/** 외부 AI 판별 구현 교체 경계 */
@FunctionalInterface
public interface HealthArticleTopicClassifier {

    /** 정제된 기사의 건강·의학·보건 관련성 판별 */
    HealthArticleTopicDecision classify(ExtractedArticle article);
}
