/* 언론사 허용 도메인 조회 저장소 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.NewsPublisherDomain;
import com.newsverification.publisher.domain.PublisherDomainStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 기사 호스트와 같은 언론사의 활성 호스트 조회 */
public interface NewsPublisherDomainRepository extends JpaRepository<NewsPublisherDomain, Long> {

    /** 정확한 호스트의 관리 상태 조회 */
    Optional<NewsPublisherDomain> findByHostname(String hostname);

    /** 언론사별 활성 호스트 조회 */
    List<NewsPublisherDomain> findAllByPublisherAndStatusOrderByHostnameAsc(
            NewsPublisher publisher,
            PublisherDomainStatus status
    );
}
