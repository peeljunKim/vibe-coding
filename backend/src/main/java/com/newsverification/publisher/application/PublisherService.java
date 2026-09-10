/* 지원 언론사 조회 유스케이스 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.PublisherStatus;
import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 공개 지원 언론사 조회 처리 */
@Service
public class PublisherService {

    private final NewsPublisherRepository publisherRepository;

    public PublisherService(NewsPublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    /** 후보 제외 지원 언론사 조회 */
    @Transactional(readOnly = true)
    public List<SupportedPublisher> findSupportedPublishers() {
        return publisherRepository.findByStatusNotOrderByNameAsc(PublisherStatus.CANDIDATE).stream()
                .map(publisher -> new SupportedPublisher(
                        publisher.name(),
                        publisher.category(),
                        publisher.status().toAvailability()
                ))
                .toList();
    }
}
