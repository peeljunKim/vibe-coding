/* DB 언론사 상태 기반 기사 수집 진입점 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.monitoring.application.OperationalMetrics;
import com.newsverification.publisher.application.PublisherDomainAccessService;
import com.newsverification.publisher.application.PublisherExtractionPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 분석 전 언론사 상태 확인과 안전 수집 연결 */
@Service
public class PublisherArticleReader {

    private final ArticleUrlValidator urlValidator;
    private final PublisherDomainAccessService domainAccessService;
    private final SafeArticleReader articleReader;
    private final OperationalMetrics metrics;
    private final PublisherExtractionPolicy publisherPolicy;

    @Autowired
    public PublisherArticleReader(
            ArticleUrlValidator urlValidator,
            PublisherDomainAccessService domainAccessService,
            SafeArticleReader articleReader,
            ObjectProvider<OperationalMetrics> metrics,
            ObjectProvider<PublisherExtractionPolicy> publisherPolicy
    ) {
        this.urlValidator = urlValidator;
        this.domainAccessService = domainAccessService;
        this.articleReader = articleReader;
        this.metrics = metrics.getIfAvailable(OperationalMetrics::disabled);
        this.publisherPolicy = publisherPolicy.getIfAvailable(PublisherExtractionPolicy::disabled);
    }

    /** Metric 비활성 테스트 구성 */
    public PublisherArticleReader(
            ArticleUrlValidator urlValidator,
            PublisherDomainAccessService domainAccessService,
            SafeArticleReader articleReader
    ) {
        this.urlValidator = urlValidator;
        this.domainAccessService = domainAccessService;
        this.articleReader = articleReader;
        this.metrics = OperationalMetrics.disabled();
        this.publisherPolicy = PublisherExtractionPolicy.disabled();
    }

    /** 기사 추출 운영 정책 테스트 구성 */
    public PublisherArticleReader(
            ArticleUrlValidator urlValidator,
            PublisherDomainAccessService domainAccessService,
            SafeArticleReader articleReader,
            PublisherExtractionPolicy publisherPolicy
    ) {
        this.urlValidator = urlValidator;
        this.domainAccessService = domainAccessService;
        this.articleReader = articleReader;
        this.metrics = OperationalMetrics.disabled();
        this.publisherPolicy = publisherPolicy;
    }

    /** 활성 언론사 기사만 안전 수집 */
    public ExtractedArticle read(String rawUrl) {
        String hostname = null;
        boolean publisherApproved = false;
        boolean extractionCompleted = false;
        try {
            hostname = urlValidator.hostname(rawUrl);
            Set<String> allowedHosts = domainAccessService.requireActivePublisherHosts(hostname);
            publisherApproved = true;
            ExtractedArticle article = articleReader.read(rawUrl, allowedHosts);
            extractionCompleted = true;
            publisherPolicy.recordSuccess(hostname);
            metrics.recordArticleExtraction(
                    OperationalMetrics.ExtractionOutcome.SUCCEEDED,
                    OperationalMetrics.ExtractionFailure.NONE
            );
            return article;
        } catch (ArticleProcessingException exception) {
            if (publisherApproved && isExtractionFailure(exception.error())) {
                publisherPolicy.recordFailure(hostname);
            }
            metrics.recordArticleExtraction(
                    OperationalMetrics.ExtractionOutcome.FAILED,
                    extractionFailure(exception.error())
            );
            throw exception;
        } catch (RuntimeException exception) {
            if (publisherApproved && !extractionCompleted) {
                publisherPolicy.recordFailure(hostname);
            }
            metrics.recordArticleExtraction(
                    OperationalMetrics.ExtractionOutcome.FAILED,
                    OperationalMetrics.ExtractionFailure.INTERNAL
            );
            throw exception;
        }
    }

    /** 사용자 입력 오류를 제외한 추출 실패 판정 */
    private boolean isExtractionFailure(ArticleProcessingError error) {
        OperationalMetrics.ExtractionFailure failure = extractionFailure(error);
        return failure == OperationalMetrics.ExtractionFailure.NETWORK
                || failure == OperationalMetrics.ExtractionFailure.CONTENT
                || failure == OperationalMetrics.ExtractionFailure.INTERNAL;
    }

    /** 상세 오류의 제한된 Metric 분류 */
    private OperationalMetrics.ExtractionFailure extractionFailure(ArticleProcessingError error) {
        return switch (error) {
            case UNSUPPORTED_PUBLISHER, PUBLISHER_TEMPORARILY_DISABLED ->
                    OperationalMetrics.ExtractionFailure.UNSUPPORTED_PUBLISHER;
            case DNS_LOOKUP_FAILED, DOWNLOAD_FAILED, HTTP_ERROR, INVALID_REDIRECT, TOO_MANY_REDIRECTS ->
                    OperationalMetrics.ExtractionFailure.NETWORK;
            case MISSING_TITLE, MISSING_BODY, MISSING_PUBLISHED_AT, INVALID_PUBLISHED_AT,
                    INVALID_MODIFIED_AT, NON_KOREAN_ARTICLE, ARTICLE_TOO_LONG,
                    UNSUPPORTED_CONTENT_TYPE, RESPONSE_TOO_LARGE -> OperationalMetrics.ExtractionFailure.CONTENT;
            default -> OperationalMetrics.ExtractionFailure.INVALID_URL;
        };
    }
}
