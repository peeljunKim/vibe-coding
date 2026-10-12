/* 언론사 운영 상태 원자 변경 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 활성 언론사의 자동 중단 전환 */
@Service
public class PublisherStatusUpdater {

    private final NewsPublisherRepository publisherRepository;

    public PublisherStatusUpdater(NewsPublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    /** ACTIVE 상태의 단일 자동 중단 */
    @Transactional
    public Optional<String> pauseAutomatically(long publisherId) {
        return publisherRepository.findById(publisherId)
                .filter(publisher -> publisherRepository.pauseAutomatically(publisherId) == 1)
                .map(publisher -> publisher.name());
    }
}
