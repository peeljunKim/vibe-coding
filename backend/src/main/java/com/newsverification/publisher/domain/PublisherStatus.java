/* 내부 언론사 운영 상태 */
package com.newsverification.publisher.domain;

/** 추출 검증과 지원 상태 */
public enum PublisherStatus {
    CANDIDATE,
    ACTIVE,
    PAUSED_AUTO,
    PAUSED_MANUAL;

    /** 공개 지원 상태 변환 */
    public PublisherAvailability toAvailability() {
        return switch (this) {
            case ACTIVE -> PublisherAvailability.ACTIVE;
            case PAUSED_AUTO, PAUSED_MANUAL -> PublisherAvailability.TEMPORARILY_DISABLED;
            case CANDIDATE -> throw new IllegalStateException("Candidate publisher is not publicly visible");
        };
    }
}
