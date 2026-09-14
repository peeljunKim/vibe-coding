/* 지원 언론사 조회 저장소 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.domain.NewsPublisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 지원 언론사 JPA 조회 */
public interface NewsPublisherRepository extends JpaRepository<NewsPublisher, Long> {

    /** 공개 목록의 표시명순 조회 */
    List<NewsPublisher> findAllByOrderByNameAsc();
}
