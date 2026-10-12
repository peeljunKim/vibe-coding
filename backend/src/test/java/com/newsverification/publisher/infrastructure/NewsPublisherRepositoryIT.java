/* Native MySQL 지원 언론사 저장소 통합 검증 */
package com.newsverification.publisher.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.publisher.application.PublisherService;
import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;
import com.newsverification.publisher.domain.PublisherDomainStatus;
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
    private NewsPublisherDomainRepository domainRepository;

    @Autowired
    private PublisherService publisherService;

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
        insertDomain("가나다 통신", "news.example", PublisherDomainStatus.ACTIVE);
        insertDomain("가나다 통신", "m.news.example", PublisherDomainStatus.ACTIVE);
        insertDomain("가나다 통신", "old.news.example", PublisherDomainStatus.PAUSED);
        insertDomain("다라마 신문", "paused.example", PublisherDomainStatus.ACTIVE);
        insertDomain("나중 후보", "candidate.example", PublisherDomainStatus.PAUSED);
    }

    /** 실제 도메인과 언론사 상태 조인 조회 검증 */
    @Test
    void findsPublisherDomainAndActiveAliasesFromNativeMySql() {
        var requested = domainRepository.findByHostname("news.example").orElseThrow();

        assertThat(requested.publisher().name()).isEqualTo("가나다 통신");
        assertThat(requested.availability()).isEqualTo(
                com.newsverification.publisher.domain.PublisherAvailability.ACTIVE);
        assertThat(domainRepository.findAllByPublisherAndStatusOrderByHostnameAsc(
                requested.publisher(), PublisherDomainStatus.ACTIVE))
                .extracting(com.newsverification.publisher.domain.NewsPublisherDomain::hostname)
                .containsExactly("m.news.example", "news.example");
        assertThat(domainRepository.findByHostname("paused.example").orElseThrow().availability())
                .isEqualTo(com.newsverification.publisher.domain.PublisherAvailability.TEMPORARILY_DISABLED);
        assertThat(domainRepository.findByHostname("candidate.example").orElseThrow().availability())
                .isEqualTo(com.newsverification.publisher.domain.PublisherAvailability.UNSUPPORTED);
    }

    /** 공개 목록의 표시명 오름차순 조회 검증 */
    @Test
    void findsPublisherDirectoryFromNativeMySqlInNameOrder() {
        List<NewsPublisher> publishers = publisherRepository.findAllByOrderByNameAsc();

        assertThat(publishers)
                .extracting(NewsPublisher::name, NewsPublisher::category, NewsPublisher::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "가나다 통신", PublisherCategory.NEWS_AGENCY, PublisherStatus.ACTIVE),
                        org.assertj.core.groups.Tuple.tuple(
                                "나중 후보", PublisherCategory.HEALTH_MEDICAL, PublisherStatus.CANDIDATE),
                        org.assertj.core.groups.Tuple.tuple(
                                "다라마 신문", PublisherCategory.GENERAL_NEWSPAPER, PublisherStatus.PAUSED_MANUAL)
                );
    }

    /** ACTIVE 상태의 단일 자동 중단 갱신 */
    @Test
    void pausesPublisherAutomaticallyOnlyOnce() {
        NewsPublisher publisher = domainRepository.findByHostname("news.example")
                .orElseThrow()
                .publisher();

        int firstUpdate = publisherRepository.pauseAutomatically(publisher.id());
        int secondUpdate = publisherRepository.pauseAutomatically(publisher.id());

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isZero();
        assertThat(publisherRepository.findById(publisher.id()).orElseThrow().status())
                .isEqualTo(PublisherStatus.PAUSED_AUTO);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auto_disabled_at IS NOT NULL FROM news_publishers WHERE id = ?",
                Boolean.class,
                publisher.id()
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status_reason FROM news_publishers WHERE id = ?",
                String.class,
                publisher.id()
        )).isEqualTo("최근 1시간 기사 추출 실패율 기준 초과");
        assertThat(publisherService.findPublisherDirectory())
                .filteredOn(entry -> entry.name().equals("가나다 통신"))
                .extracting(entry -> entry.status())
                .containsExactly(PublisherAvailability.TEMPORARILY_DISABLED);
    }

    /** 자동 중단 상태만 수동 복구 허용 */
    @Test
    void reactivatesOnlyAutomaticallyPausedPublisher() {
        NewsPublisher publisher = domainRepository.findByHostname("news.example")
                .orElseThrow()
                .publisher();
        assertThat(publisherRepository.pauseAutomatically(publisher.id())).isEqualTo(1);

        int reactivated = jdbcTemplate.update(
                """
                UPDATE news_publishers
                SET status = 'ACTIVE',
                    status_reason = '수동 재활성화: 추출 보완 및 재시험 통과',
                    auto_disabled_at = NULL,
                    status_changed_at = CURRENT_TIMESTAMP(6),
                    version = version + 1
                WHERE id = ?
                  AND status = 'PAUSED_AUTO'
                """,
                publisher.id()
        );
        int duplicate = jdbcTemplate.update(
                "UPDATE news_publishers SET status = 'ACTIVE' WHERE id = ? AND status = 'PAUSED_AUTO'",
                publisher.id()
        );

        assertThat(reactivated).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(publisherRepository.findById(publisher.id()).orElseThrow().status())
                .isEqualTo(PublisherStatus.ACTIVE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT auto_disabled_at IS NULL FROM news_publishers WHERE id = ?",
                Boolean.class,
                publisher.id()
        )).isTrue();
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

    /** 테스트 Fixture 도메인 입력 */
    private void insertDomain(String publisherName, String hostname, PublisherDomainStatus status) {
        int updated = jdbcTemplate.update(
                """
                INSERT INTO news_publisher_domains (publisher_id, hostname, status)
                SELECT id, ?, ? FROM news_publishers WHERE name = ?
                """,
                hostname,
                status.name(),
                publisherName
        );
        assertThat(updated).isEqualTo(1);
    }

}
