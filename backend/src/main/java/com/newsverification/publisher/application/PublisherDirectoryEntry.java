/* 공개 언론사 목록 항목 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;

/** 공개 언론사 상태 정보 */
public record PublisherDirectoryEntry(
        String name,
        PublisherCategory category,
        PublisherAvailability status
) {
}
