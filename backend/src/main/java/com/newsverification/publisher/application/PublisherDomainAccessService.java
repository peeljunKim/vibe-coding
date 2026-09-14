/* 기사 추출용 언론사 도메인 상태 판정 */
package com.newsverification.publisher.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.publisher.domain.NewsPublisherDomain;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherDomainStatus;
import com.newsverification.publisher.infrastructure.NewsPublisherDomainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/** DB 운영 상태 기반 기사 추출 허용 호스트 결정 */
@Service
public class PublisherDomainAccessService {

    private final NewsPublisherDomainRepository domainRepository;

    public PublisherDomainAccessService(NewsPublisherDomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    /** 활성 언론사의 Redirect 허용 호스트 조회 */
    @Transactional(readOnly = true)
    public Set<String> requireActivePublisherHosts(String hostname) {
        NewsPublisherDomain requestedDomain = domainRepository.findByHostname(hostname)
                .orElseThrow(() -> new ArticleProcessingException(
                        ArticleProcessingError.UNSUPPORTED_PUBLISHER));

        PublisherAvailability availability = requestedDomain.availability();
        if (availability == PublisherAvailability.UNSUPPORTED) {
            throw new ArticleProcessingException(ArticleProcessingError.UNSUPPORTED_PUBLISHER);
        }
        if (availability == PublisherAvailability.TEMPORARILY_DISABLED) {
            throw new ArticleProcessingException(
                    ArticleProcessingError.PUBLISHER_TEMPORARILY_DISABLED);
        }

        return domainRepository.findAllByPublisherAndStatusOrderByHostnameAsc(
                        requestedDomain.publisher(), PublisherDomainStatus.ACTIVE)
                .stream()
                .map(NewsPublisherDomain::hostname)
                .collect(Collectors.toUnmodifiableSet());
    }
}
