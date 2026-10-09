/* 분석·Cache·기사 수집 업무 Metric 경계 */
package com.newsverification.monitoring.application;

import java.time.Duration;

/** 제한된 Label만 허용하는 업무 Metric Port */
public interface OperationalMetrics {

    enum Feature {
        HEALTH,
        HEADLINE
    }

    enum RequestOutcome {
        ACCEPTED,
        QUEUE_FULL,
        REDIS_UNAVAILABLE
    }

    enum WorkerOutcome {
        COMPLETED,
        FAILED
    }

    enum CacheOutcome {
        HIT,
        MISS
    }

    enum ExtractionOutcome {
        SUCCEEDED,
        FAILED
    }

    enum ExtractionFailure {
        NONE,
        INVALID_URL,
        UNSUPPORTED_PUBLISHER,
        NETWORK,
        CONTENT,
        INTERNAL
    }

    void recordAnalysisRequest(Feature feature, RequestOutcome outcome);

    void recordWorkerResult(Feature feature, WorkerOutcome outcome);

    void recordWorkerDuration(Feature feature, Duration duration);

    void recordCacheLookup(Feature feature, CacheOutcome outcome);

    void recordArticleExtraction(ExtractionOutcome outcome, ExtractionFailure failure);

    static OperationalMetrics disabled() {
        return DisabledOperationalMetrics.INSTANCE;
    }

    /** 테스트와 비활성 경계의 무기록 구현 */
    enum DisabledOperationalMetrics implements OperationalMetrics {
        INSTANCE;

        @Override
        public void recordAnalysisRequest(Feature feature, RequestOutcome outcome) {
        }

        @Override
        public void recordWorkerResult(Feature feature, WorkerOutcome outcome) {
        }

        @Override
        public void recordWorkerDuration(Feature feature, Duration duration) {
        }

        @Override
        public void recordCacheLookup(Feature feature, CacheOutcome outcome) {
        }

        @Override
        public void recordArticleExtraction(ExtractionOutcome outcome, ExtractionFailure failure) {
        }
    }
}
