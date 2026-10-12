/* 언론사 추출 실패율 기반 자동 중단 정책 */
package com.newsverification.publisher.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 최근 집계와 원자 상태 전환 연결 */
public class PublisherAutoPausePolicy implements PublisherExtractionPolicy {

    private static final Logger log = LoggerFactory.getLogger(PublisherAutoPausePolicy.class);

    private final PublisherExtractionAttemptStore attemptStore;
    private final PublisherStatusUpdater statusUpdater;
    private final PublisherAlertPort alertPort;

    public PublisherAutoPausePolicy(
            PublisherExtractionAttemptStore attemptStore,
            PublisherStatusUpdater statusUpdater,
            PublisherAlertPort alertPort
    ) {
        this.attemptStore = attemptStore;
        this.statusUpdater = statusUpdater;
        this.alertPort = alertPort;
    }

    /** 성공 시도 집계 */
    @Override
    public void recordSuccess(long publisherId) {
        evaluate(publisherId, false);
    }

    /** 추출 실패 집계 */
    @Override
    public void recordFailure(long publisherId) {
        evaluate(publisherId, true);
    }

    private void evaluate(long publisherId, boolean failed) {
        if (!attemptStore.record(publisherId, failed)) {
            return;
        }
        statusUpdater.pauseAutomatically(publisherId).ifPresent(this::notifySafely);
    }

    private void notifySafely(String publisherName) {
        try {
            alertPort.notifyAutomaticallyPaused(publisherName);
        } catch (RuntimeException exception) {
            log.warn(
                    "Publisher automatic pause notification failed: cause={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
