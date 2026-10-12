/* 지원 언론사 조회 저장소 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.domain.NewsPublisher;
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
    @Query(value = """
            UPDATE news_publishers
            SET status = 'PAUSED_AUTO',
                status_reason = '최근 1시간 기사 추출 실패율 기준 초과',
                auto_disabled_at = CURRENT_TIMESTAMP(6),
                status_changed_at = CURRENT_TIMESTAMP(6),
                version = version + 1
            WHERE id = :publisherId
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    int pauseAutomatically(@Param("publisherId") Long publisherId);
}
