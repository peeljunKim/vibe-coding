/* 언론사 허용 도메인 영속 모델 */
package com.newsverification.publisher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 언론사별 허용 호스트 */
@Entity
@Table(name = "news_publisher_domains")
public class NewsPublisherDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publisher_id", nullable = false)
    private NewsPublisher publisher;

    @Column(nullable = false, length = 253)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PublisherDomainStatus status;

    protected NewsPublisherDomain() {
    }

    /** 소속 언론사 조회 */
    public NewsPublisher publisher() {
        return publisher;
    }

    /** 허용 호스트명 조회 */
    public String hostname() {
        return hostname;
    }

    /** 기사 추출 가능 상태 계산 */
    public PublisherAvailability availability() {
        PublisherAvailability publisherAvailability = publisher.status().toAvailability();
        if (publisherAvailability != PublisherAvailability.ACTIVE) {
            return publisherAvailability;
        }
        return status == PublisherDomainStatus.ACTIVE
                ? PublisherAvailability.ACTIVE
                : PublisherAvailability.TEMPORARILY_DISABLED;
    }
}
