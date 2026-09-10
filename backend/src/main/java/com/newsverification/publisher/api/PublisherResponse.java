/* 지원 언론사 API 응답 */
package com.newsverification.publisher.api;

import com.newsverification.publisher.application.SupportedPublisher;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;

/** 지원 언론사 공개 응답 */
public record PublisherResponse(
        String name,
        PublisherCategory category,
        PublisherAvailability status
) {

    /** 조회 결과의 API 응답 변환 */
    static PublisherResponse from(SupportedPublisher publisher) {
        return new PublisherResponse(publisher.name(), publisher.category(), publisher.status());
    }
}
