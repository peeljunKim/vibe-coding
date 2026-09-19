/* 기사 제목 분석 Use Case 검증 */
package com.newsverification.headline.application;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.article.domain.ExtractedArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 건강 분야 판별 없는 제목·본문 비교 경계 검증 */
class HeadlineAnalysisUseCaseTest {

    /** 지원 언론사의 일반 기사도 제목 분석으로 전달 */
    @Test
    void analyzesExtractedArticleWithoutHealthTopicScreening() {
        PublisherArticleReader articleReader = mock(PublisherArticleReader.class);
        HeadlineAnalysisPort analysisPort = mock(HeadlineAnalysisPort.class);
        ExtractedArticle article = new ExtractedArticle(
                URI.create("https://news.example/general"),
                "일반 기사 제목",
                "일반 기사 본문",
                OffsetDateTime.parse("2026-09-19T09:00:00+09:00"),
                Optional.empty()
        );
        Instant deadlineAt = Instant.parse("2026-09-19T01:01:30Z");
        HeadlineAnalysisResult expected = mock(HeadlineAnalysisResult.class);
        when(articleReader.read(article.sourceUrl().toString())).thenReturn(article);
        when(analysisPort.analyze(article, deadlineAt)).thenReturn(expected);

        HeadlineAnalysisResult result = new HeadlineAnalysisUseCase(articleReader, analysisPort)
                .analyze(article.sourceUrl().toString(), deadlineAt);

        assertThat(result).isSameAs(expected);
        verify(articleReader).read(article.sourceUrl().toString());
        verify(analysisPort).analyze(article, deadlineAt);
    }
}
