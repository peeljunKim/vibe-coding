/* 기사 제목 분석 Use Case */
package com.newsverification.headline.application;

import com.newsverification.article.application.PublisherArticleReader;
import com.newsverification.article.domain.ExtractedArticle;

import java.time.Instant;
import java.util.Objects;

/** 안전 수집 기사와 제목 분석 Port 연결 */
public class HeadlineAnalysisUseCase {

    private final PublisherArticleReader articleReader;
    private final HeadlineAnalysisPort analysisPort;

    /** 기사 수집과 제목 분석 경계 구성 */
    public HeadlineAnalysisUseCase(
            PublisherArticleReader articleReader,
            HeadlineAnalysisPort analysisPort
    ) {
        this.articleReader = Objects.requireNonNull(articleReader);
        this.analysisPort = Objects.requireNonNull(analysisPort);
    }

    /** 건강 분야 판별 없는 제목·본문 분석 */
    public HeadlineAnalysisResult analyze(String rawUrl, Instant deadlineAt) {
        return analyze(read(rawUrl), deadlineAt);
    }

    /** 안전 검증과 본문 추출 */
    public ExtractedArticle read(String rawUrl) {
        return articleReader.read(rawUrl);
    }

    /** 추출 성공 이후 제목·본문 분석 */
    public HeadlineAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
        return analysisPort.analyze(article, deadlineAt);
    }
}
