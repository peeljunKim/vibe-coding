/* 언론사 추출 실패 자동 중단 정책 검증 */
package com.newsverification.publisher.application;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 집계 결과와 원자 상태 전환 기반 알림 검증 */
class PublisherAutoPausePolicyTest {

    private static final String HOSTNAME = "news.example";

    /** 임계치 미달 상태 유지 */
    @Test
    void keepsPublisherActiveBelowThreshold() {
        PublisherExtractionAttemptStore attemptStore = mock(PublisherExtractionAttemptStore.class);
        PublisherStatusUpdater statusUpdater = mock(PublisherStatusUpdater.class);
        PublisherAlertPort alertPort = mock(PublisherAlertPort.class);
        when(attemptStore.record(HOSTNAME, true)).thenReturn(false);
        PublisherAutoPausePolicy policy = new PublisherAutoPausePolicy(
                attemptStore, statusUpdater, alertPort);

        policy.recordFailure(HOSTNAME);

        verify(statusUpdater, never()).pauseAutomatically(HOSTNAME);
        verify(alertPort, never()).notifyAutomaticallyPaused("언론사");
    }

    /** 임계치 충족 자동 중단과 관리자 알림 */
    @Test
    void pausesPublisherAndNotifiesAdministratorAtThreshold() {
        PublisherExtractionAttemptStore attemptStore = mock(PublisherExtractionAttemptStore.class);
        PublisherStatusUpdater statusUpdater = mock(PublisherStatusUpdater.class);
        PublisherAlertPort alertPort = mock(PublisherAlertPort.class);
        when(attemptStore.record(HOSTNAME, true)).thenReturn(true);
        when(statusUpdater.pauseAutomatically(HOSTNAME)).thenReturn(Optional.of("언론사"));
        PublisherAutoPausePolicy policy = new PublisherAutoPausePolicy(
                attemptStore, statusUpdater, alertPort);

        policy.recordFailure(HOSTNAME);

        verify(alertPort).notifyAutomaticallyPaused("언론사");
    }

    /** 이미 중단된 언론사의 중복 알림 차단 */
    @Test
    void doesNotNotifyWhenPublisherWasAlreadyPaused() {
        PublisherExtractionAttemptStore attemptStore = mock(PublisherExtractionAttemptStore.class);
        PublisherStatusUpdater statusUpdater = mock(PublisherStatusUpdater.class);
        PublisherAlertPort alertPort = mock(PublisherAlertPort.class);
        when(attemptStore.record(HOSTNAME, true)).thenReturn(true);
        when(statusUpdater.pauseAutomatically(HOSTNAME)).thenReturn(Optional.empty());
        PublisherAutoPausePolicy policy = new PublisherAutoPausePolicy(
                attemptStore, statusUpdater, alertPort);

        policy.recordFailure(HOSTNAME);

        verify(alertPort, never()).notifyAutomaticallyPaused("언론사");
    }

    /** 알림 장애와 완료된 상태 전환 분리 */
    @Test
    void keepsAutomaticPauseWhenNotificationFails() {
        PublisherExtractionAttemptStore attemptStore = mock(PublisherExtractionAttemptStore.class);
        PublisherStatusUpdater statusUpdater = mock(PublisherStatusUpdater.class);
        PublisherAlertPort alertPort = mock(PublisherAlertPort.class);
        when(attemptStore.record(HOSTNAME, true)).thenReturn(true);
        when(statusUpdater.pauseAutomatically(HOSTNAME)).thenReturn(Optional.of("언론사"));
        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(alertPort).notifyAutomaticallyPaused("언론사");
        PublisherAutoPausePolicy policy = new PublisherAutoPausePolicy(
                attemptStore, statusUpdater, alertPort);

        policy.recordFailure(HOSTNAME);

        verify(alertPort).notifyAutomaticallyPaused("언론사");
    }
}
