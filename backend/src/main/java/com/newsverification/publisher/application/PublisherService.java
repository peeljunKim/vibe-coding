/* 지원 언론사 조회 유스케이스 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 분야와 무관한 공개 언론사 상태 조회 처리 */
@Service
public class PublisherService {

    private final NewsPublisherRepository publisherRepository;

    public PublisherService(NewsPublisherRepository publisherRepository) {
        this.publisherRepository = publisherRepository;
    }

    /** 분류와 무관한 공개 언론사 목록 조회 */
    @Transactional(readOnly = true)
    public List<PublisherDirectoryEntry> findPublisherDirectory() {
        return publisherRepository.findAllByOrderByNameAsc().stream()
                .map(publisher -> new PublisherDirectoryEntry(
                        publisher.name(),
                        publisher.category(),
                        publisher.status().toAvailability()
                ))
                .toList();
    }
}
