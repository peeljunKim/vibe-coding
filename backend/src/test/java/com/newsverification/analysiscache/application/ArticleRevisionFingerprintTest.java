/* 기사 변경 Fingerprint 검증 */
package com.newsverification.analysiscache.application;

import com.newsverification.article.domain.ExtractedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 제목·시각·문단 Hash 기반 기사 동일성 검증 */
class ArticleRevisionFingerprintTest {

    /** 공백과 시각 Offset 표현 차이를 제외한 동일 기사 판별 */
    @Test
    void createsSameFingerprintForEquivalentArticle() {
        ExtractedArticle first = article(
                "건강  기사 제목",
                "첫 번째 문단입니다.\n두 번째 문단입니다.",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        );
        ExtractedArticle second = article(
                " 건강 기사   제목 ",
                "첫 번째   문단입니다.\n\n두 번째 문단입니다.",
                "2026-09-30T00:00:00Z",
                "2026-09-30T01:00:00Z"
        );

        assertThat(ArticleRevisionFingerprint.from(first))
                .isEqualTo(ArticleRevisionFingerprint.from(second));
    }

    /** 제목·게시·수정 시각 변경 판별 */
    @Test
    void detectsTitleAndTimestampChanges() {
        ArticleRevisionFingerprint original = ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "첫 문단\n둘째 문단",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ));

        assertThat(ArticleRevisionFingerprint.from(article(
                "변경 제목",
                "첫 문단\n둘째 문단",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ))).isNotEqualTo(original);
        assertThat(ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "첫 문단\n둘째 문단",
                "2026-09-30T09:01:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ))).isNotEqualTo(original);
        assertThat(ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "첫 문단\n둘째 문단",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:01:00+09:00"
        ))).isNotEqualTo(original);
    }

    /** 문단 내용과 순서 변경 판별 */
    @Test
    void detectsParagraphContentAndOrderChangesWithoutKeepingArticleText() {
        ArticleRevisionFingerprint original = ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "첫 문단 비밀 내용\n둘째 문단 비밀 내용",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ));
        ArticleRevisionFingerprint changed = ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "첫 문단 변경 내용\n둘째 문단 비밀 내용",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ));
        ArticleRevisionFingerprint reordered = ArticleRevisionFingerprint.from(article(
                "기존 제목",
                "둘째 문단 비밀 내용\n첫 문단 비밀 내용",
                "2026-09-30T09:00:00+09:00",
                "2026-09-30T10:00:00+09:00"
        ));

        assertThat(changed).isNotEqualTo(original);
        assertThat(reordered).isNotEqualTo(original);
        assertThat(original.toString()).doesNotContain("비밀 내용", "기존 제목");
        assertThat(original.paragraphHashes()).allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    /** 기사 Fixture 생성 */
    private ExtractedArticle article(
            String title,
            String body,
            String publishedAt,
            String modifiedAt
    ) {
        return new ExtractedArticle(
                URI.create("https://news.example/article"),
                title,
                body,
                OffsetDateTime.parse(publishedAt),
                Optional.of(OffsetDateTime.parse(modifiedAt))
        );
    }
}
