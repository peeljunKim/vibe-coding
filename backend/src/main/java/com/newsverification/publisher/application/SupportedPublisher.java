/* 지원 언론사 조회 결과 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;

/** 공개 지원 언론사 정보 */
public record SupportedPublisher(
        String name,
        PublisherCategory category,
        PublisherAvailability status
) {
}
