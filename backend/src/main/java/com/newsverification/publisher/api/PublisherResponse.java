/* 공개 언론사 상태 API 응답 */
package com.newsverification.publisher.api;

import com.newsverification.publisher.application.PublisherDirectoryEntry;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;

/** 공개 언론사 상태 응답 */
public record PublisherResponse(
        String name,
        PublisherCategory category,
        PublisherAvailability status
) {

    /** 조회 결과의 API 응답 변환 */
    static PublisherResponse from(PublisherDirectoryEntry publisher) {
        return new PublisherResponse(publisher.name(), publisher.category(), publisher.status());
    }
}
