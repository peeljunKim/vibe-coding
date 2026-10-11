/* 기사 수집 결과 기반 언론사 운영 정책 */
package com.newsverification.publisher.application;

/** 기사 추출 성공·실패 기록 경계 */
public interface PublisherExtractionPolicy {

    void recordSuccess(String hostname);

    void recordFailure(String hostname);

    static PublisherExtractionPolicy disabled() {
        return DisabledPublisherExtractionPolicy.INSTANCE;
    }

    /** 테스트와 비활성 경계의 무기록 구현 */
    enum DisabledPublisherExtractionPolicy implements PublisherExtractionPolicy {
        INSTANCE;

        @Override
        public void recordSuccess(String hostname) {
        }

        @Override
        public void recordFailure(String hostname) {
        }
    }
}
