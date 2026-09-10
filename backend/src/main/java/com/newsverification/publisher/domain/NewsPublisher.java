/* 지원 언론사 영속 모델 */
package com.newsverification.publisher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 지원 언론사와 운영 상태 */
@Entity
@Table(name = "news_publishers")
public class NewsPublisher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PublisherCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PublisherStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected NewsPublisher() {
    }

    /** 언론사 표시명 조회 */
    public String name() {
        return name;
    }

    /** 언론사 분류 조회 */
    public PublisherCategory category() {
        return category;
    }

    /** 내부 운영 상태 조회 */
    public PublisherStatus status() {
        return status;
    }
}
