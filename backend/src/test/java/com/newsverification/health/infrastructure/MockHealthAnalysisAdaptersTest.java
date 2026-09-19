/* 건강 분석 Local Mock 동작 검증 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.HealthArticleTopicDecision;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 호출 없는 분야 판별과 구조화 결과 */
class MockHealthAnalysisAdaptersTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");

    /** 건강·일반·불명확 기사 판별 */
    @Test
    void classifiesFixtureArticlesDeterministically() {
        var classifier = new MockHealthArticleTopicClassifier();

        assertThat(classifier.classify(article(
                "독감 예방접종 대상 안내",
                "질병관리청이 예방접종 시기를 안내했습니다."
        ))).isEqualTo(HealthArticleTopicDecision.HEALTH_RELATED);
        assertThat(classifier.classify(article(
                "지역 축제 개막",
                "행사장 주변 교통 통제 시간이 발표됐습니다."
        ))).isEqualTo(HealthArticleTopicDecision.NOT_HEALTH_RELATED);
        assertThat(classifier.classify(article(
                "생활 습관 변화",
                "전문가 의견을 더 살펴봐야 합니다."
        ))).isEqualTo(HealthArticleTopicDecision.UNCERTAIN);
    }

    /** 정제 기사 기반 근거 부족 결과 생성 */
    @Test
    void createsStructuredMockResultBeforeDeadline() {
        var port = new MockHealthAnalysisPort(Clock.fixed(NOW, ZoneOffset.UTC));

        HealthAnalysisResult result = port.analyze(
                article("독감 예방접종 대상 안내", "건강 기사 본문"),
                NOW.plusSeconds(30)
        );

        assertThat(result.analyzedAt()).isEqualTo(NOW);
        assertThat(result.overallStatus()).isEqualTo(HealthAnalysisResult.OverallStatus.CAUTION);
        assertThat(result.claims()).hasSize(1);
        assertThat(result.limitedEvidence()).isTrue();
    }

    /** 기한 이후 Mock 분석 차단 */
    @Test
    void rejectsAnalysisAtDeadline() {
        var port = new MockHealthAnalysisPort(Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> port.analyze(
                article("독감 예방접종 대상 안내", "건강 기사 본문"),
                NOW
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Health analysis deadline exceeded");
    }

    /** 고정 정제 기사 */
    private ExtractedArticle article(String title, String body) {
        return new ExtractedArticle(
                URI.create("https://news.example/article/1"),
                title,
                body,
                OffsetDateTime.parse("2026-08-14T09:30:00+09:00"),
                Optional.empty()
        );
    }
}
