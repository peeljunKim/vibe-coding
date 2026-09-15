/* DB 언론사 상태 기반 기사 수집 진입점 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.publisher.application.PublisherDomainAccessService;
import org.springframework.stereotype.Service;

import java.util.Set;

/** 분석 전 언론사 상태 확인과 안전 수집 연결 */
@Service
public class PublisherArticleReader {

    private final ArticleUrlValidator urlValidator;
    private final PublisherDomainAccessService domainAccessService;
    private final SafeArticleReader articleReader;

    public PublisherArticleReader(
            ArticleUrlValidator urlValidator,
            PublisherDomainAccessService domainAccessService,
            SafeArticleReader articleReader
    ) {
        this.urlValidator = urlValidator;
        this.domainAccessService = domainAccessService;
        this.articleReader = articleReader;
    }

    /** 활성 언론사 기사만 안전 수집 */
    public ExtractedArticle read(String rawUrl) {
        String hostname = urlValidator.hostname(rawUrl);
        Set<String> allowedHosts = domainAccessService.requireActivePublisherHosts(hostname);
        return articleReader.read(rawUrl, allowedHosts);
    }
}
