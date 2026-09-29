/* 분석 공용 Cache Key 검증 */
package com.newsverification.analysiscache.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** URL 정규화와 기능·버전별 Cache 격리 검증 */
class AnalysisCacheKeyFactoryTest {

    private static final AnalysisCacheVersions VERSIONS = new AnalysisCacheVersions(
            "health-model-v1",
            "health-policy-v1",
            "evidence-v1",
            "headline-model-v1",
            "headline-policy-v1",
            "publisher-policy-v1"
    );

    /** 동일 URL 표현의 동일 Key 변환 */
    @Test
    void normalizesEquivalentArticleUrls() {
        AnalysisCacheKey first = AnalysisCacheKeyFactory.health(
                " HTTPS://NEWS.EXAMPLE:443/a/../article?id=1#section ",
                VERSIONS
        ).orElseThrow();
        AnalysisCacheKey second = AnalysisCacheKeyFactory.health(
                "https://news.example/article?id=1",
                VERSIONS
        ).orElseThrow();

        assertThat(first).isEqualTo(second);
        assertThat(first.id()).hasSize(64).doesNotContain("news.example");
    }

    /** 건강·제목 기능 Namespace 분리 */
    @Test
    void separatesHealthAndHeadlineKeys() {
        String articleUrl = "https://news.example/article";

        AnalysisCacheKey health = AnalysisCacheKeyFactory.health(articleUrl, VERSIONS)
                .orElseThrow();
        AnalysisCacheKey headline = AnalysisCacheKeyFactory.headline(articleUrl, VERSIONS)
                .orElseThrow();

        assertThat(health.feature()).isEqualTo(AnalysisCacheFeature.HEALTH);
        assertThat(headline.feature()).isEqualTo(AnalysisCacheFeature.HEADLINE);
        assertThat(health.id()).isNotEqualTo(headline.id());
    }

    /** 모델·판정·출처·언론사 정책 Version 변경의 Key 무효화 */
    @Test
    void changesKeyWhenAnyRelevantVersionChanges() {
        String articleUrl = "https://news.example/article";
        AnalysisCacheKey originalHealth = AnalysisCacheKeyFactory.health(articleUrl, VERSIONS)
                .orElseThrow();
        AnalysisCacheKey changedHealth = AnalysisCacheKeyFactory.health(
                articleUrl,
                new AnalysisCacheVersions(
                        "health-model-v2",
                        "health-policy-v1",
                        "evidence-v1",
                        "headline-model-v1",
                        "headline-policy-v1",
                        "publisher-policy-v1"
                )
        ).orElseThrow();
        AnalysisCacheKey changedEvidence = AnalysisCacheKeyFactory.health(
                articleUrl,
                new AnalysisCacheVersions(
                        "health-model-v1",
                        "health-policy-v1",
                        "evidence-v2",
                        "headline-model-v1",
                        "headline-policy-v1",
                        "publisher-policy-v1"
                )
        ).orElseThrow();
        AnalysisCacheKey originalHeadline = AnalysisCacheKeyFactory.headline(articleUrl, VERSIONS)
                .orElseThrow();
        AnalysisCacheKey changedPublisher = AnalysisCacheKeyFactory.headline(
                articleUrl,
                new AnalysisCacheVersions(
                        "health-model-v1",
                        "health-policy-v1",
                        "evidence-v1",
                        "headline-model-v1",
                        "headline-policy-v1",
                        "publisher-policy-v2"
                )
        ).orElseThrow();

        assertThat(changedHealth.id()).isNotEqualTo(originalHealth.id());
        assertThat(changedEvidence.id()).isNotEqualTo(originalHealth.id());
        assertThat(changedPublisher.id()).isNotEqualTo(originalHeadline.id());
    }

    /** Cache 이전의 잘못된 URL 처리 보류 */
    @Test
    void skipsInvalidOrNonHttpsUrls() {
        assertThat(AnalysisCacheKeyFactory.health("http://news.example/article", VERSIONS))
                .isEmpty();
        assertThat(AnalysisCacheKeyFactory.headline("not-a-url", VERSIONS))
                .isEmpty();
    }
}
