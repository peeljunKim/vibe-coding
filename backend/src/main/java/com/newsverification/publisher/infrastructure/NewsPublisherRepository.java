/* 지원 언론사 조회 저장소 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.PublisherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 지원 언론사 JPA 조회 */
public interface NewsPublisherRepository extends JpaRepository<NewsPublisher, Long> {

    /** 공개 목록의 표시명순 조회 */
    List<NewsPublisher> findAllByOrderByNameAsc();

    /** 활성 상태일 때만 원자 자동 중단 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NewsPublisher publisher
            set publisher.status = :pausedStatus,
                publisher.version = publisher.version + 1
            where publisher.id = :publisherId
              and publisher.status = :activeStatus
            """)
    int pauseAutomatically(
            @Param("publisherId") Long publisherId,
            @Param("activeStatus") PublisherStatus activeStatus,
            @Param("pausedStatus") PublisherStatus pausedStatus
    );
}
