/* 언론사 운영 상태 원자 변경 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.infrastructure.NewsPublisherDomainRepository;
import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 활성 언론사의 자동 중단 전환 */
@Service
public class PublisherStatusUpdater {

    private final NewsPublisherDomainRepository domainRepository;
    private final NewsPublisherRepository publisherRepository;

    public PublisherStatusUpdater(
            NewsPublisherDomainRepository domainRepository,
            NewsPublisherRepository publisherRepository
    ) {
        this.domainRepository = domainRepository;
        this.publisherRepository = publisherRepository;
    }

    /** ACTIVE 상태의 단일 자동 중단 */
    @Transactional
    public Optional<String> pauseAutomatically(String hostname) {
        return domainRepository.findByHostname(hostname)
                .filter(domain -> publisherRepository.pauseAutomatically(domain.publisher().id()) == 1)
                .map(domain -> domain.publisher().name());
    }
}
