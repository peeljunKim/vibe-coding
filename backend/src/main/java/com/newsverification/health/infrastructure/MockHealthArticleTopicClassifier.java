/* Local 건강 기사 분야 판별 Mock */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthArticleTopicClassifier;
import com.newsverification.health.application.HealthArticleTopicDecision;

import java.util.List;

/** 외부 AI 호출 없는 개발용 키워드 판별 */
public class MockHealthArticleTopicClassifier implements HealthArticleTopicClassifier {

    private static final List<String> HEALTH_KEYWORDS = List.of(
            "건강", "의학", "질병", "병원", "치료", "예방접종", "독감", "암", "혈압", "의료"
    );
    private static final List<String> GENERAL_KEYWORDS = List.of(
            "축제", "교통", "선거", "증시", "스포츠", "공연"
    );

    /** 제목과 정제 본문의 개발용 고정 판별 */
    @Override
    public HealthArticleTopicDecision classify(ExtractedArticle article) {
        String content = article.title() + " " + article.body();
        if (containsAny(content, HEALTH_KEYWORDS)) {
            return HealthArticleTopicDecision.HEALTH_RELATED;
        }
        if (containsAny(content, GENERAL_KEYWORDS)) {
            return HealthArticleTopicDecision.NOT_HEALTH_RELATED;
        }
        return HealthArticleTopicDecision.UNCERTAIN;
    }

    /** 후보 단어 포함 여부 */
    private boolean containsAny(String content, List<String> keywords) {
        return keywords.stream().anyMatch(content::contains);
    }
}
