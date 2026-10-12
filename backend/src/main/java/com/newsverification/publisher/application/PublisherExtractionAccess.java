/* 기사 추출 허용 언론사 식별 정보 */
package com.newsverification.publisher.application;

import java.util.Objects;
import java.util.Set;

/** 언론사 ID와 Redirect 허용 호스트 */
public record PublisherExtractionAccess(long publisherId, Set<String> allowedHosts) {

    public PublisherExtractionAccess {
        if (publisherId < 1) {
            throw new IllegalArgumentException("Publisher ID must be positive");
        }
        allowedHosts = Set.copyOf(Objects.requireNonNull(allowedHosts));
        if (allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("Publisher allowed hosts are required");
        }
    }
}
