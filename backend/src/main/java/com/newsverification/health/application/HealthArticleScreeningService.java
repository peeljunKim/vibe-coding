/* 건강 기사 분야 판별 흐름 */
package com.newsverification.health.application;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.article.domain.ExtractedArticle;

/** 안전 수집 이후 건강 기사 판별 연결 */
public class HealthArticleScreeningService {

    private final PublisherArticleReader articleReader;
    private final HealthArticleTopicClassifier topicClassifier;

    /** 기사 수집과 분야 판별 경계 구성 */
    public HealthArticleScreeningService(
            PublisherArticleReader articleReader,
            HealthArticleTopicClassifier topicClassifier
    ) {
        this.articleReader = articleReader;
        this.topicClassifier = topicClassifier;
    }

    /** 지원 언론사 기사 수집 후 분야 판별 */
    public HealthArticleScreeningResult screen(String rawUrl) {
        ExtractedArticle article = articleReader.read(rawUrl);
        HealthArticleTopicDecision decision = topicClassifier.classify(article);
        return new HealthArticleScreeningResult(article, decision);
    }
}
