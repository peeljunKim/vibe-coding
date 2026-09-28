/* 만료 제목 공유 정리 경계 검증 */
package com.newsverification.share.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 현재 시각 기준 만료 Snapshot 삭제 검증 */
class ExpiredHeadlineShareCleanupServiceTest {

    @Test
    void deletesExpiredHeadlineSharesAtCurrentInstant() {
        Instant now = Instant.parse("2026-09-28T01:00:00Z");
        ShareStore store = mock(ShareStore.class);
        when(store.deleteExpiredHeadline(now)).thenReturn(3);
        ExpiredHeadlineShareCleanupService service = new ExpiredHeadlineShareCleanupService(
                store,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(service.cleanup()).isEqualTo(3);
        verify(store).deleteExpiredHeadline(now);
    }
}
