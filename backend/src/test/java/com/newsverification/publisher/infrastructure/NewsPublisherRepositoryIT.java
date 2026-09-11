/* Native MySQL 지원 언론사 저장소 통합 검증 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.PublisherCategory;
import com.newsverification.publisher.domain.PublisherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL 조회와 Entity 매핑 검증 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class NewsPublisherRepositoryIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();

    @Autowired
    private NewsPublisherRepository publisherRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 테스트 전용 DataSource 설정 */
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_CONNECTION::databaseUrl);
        registry.add("spring.datasource.username", TEST_CONNECTION::username);
        registry.add("spring.datasource.password", TEST_CONNECTION::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** 격리된 Fixture 준비 */
    @BeforeEach
    void preparePublishers() {
        jdbcTemplate.update("DELETE FROM news_publisher_domains");
        jdbcTemplate.update("DELETE FROM news_publishers");
        insertPublisher("가나다 통신", PublisherCategory.NEWS_AGENCY, PublisherStatus.ACTIVE);
        insertPublisher("다라마 신문", PublisherCategory.GENERAL_NEWSPAPER, PublisherStatus.PAUSED_MANUAL);
        insertPublisher("나중 후보", PublisherCategory.HEALTH_MEDICAL, PublisherStatus.CANDIDATE);
    }

    /** 후보 제외와 표시명 오름차순 조회 검증 */
    @Test
    void findsSupportedPublishersFromNativeMySqlInNameOrder() {
        List<NewsPublisher> publishers =
                publisherRepository.findByStatusNotOrderByNameAsc(PublisherStatus.CANDIDATE);

        assertThat(publishers)
                .extracting(NewsPublisher::name, NewsPublisher::category, NewsPublisher::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "가나다 통신", PublisherCategory.NEWS_AGENCY, PublisherStatus.ACTIVE),
                        org.assertj.core.groups.Tuple.tuple(
                                "다라마 신문", PublisherCategory.GENERAL_NEWSPAPER, PublisherStatus.PAUSED_MANUAL)
                );
    }

    /** 테스트 Fixture 입력 */
    private void insertPublisher(String name, PublisherCategory category, PublisherStatus status) {
        jdbcTemplate.update(
                "INSERT INTO news_publishers (name, category, status) VALUES (?, ?, ?)",
                name,
                category.name(),
                status.name()
        );
    }

}
