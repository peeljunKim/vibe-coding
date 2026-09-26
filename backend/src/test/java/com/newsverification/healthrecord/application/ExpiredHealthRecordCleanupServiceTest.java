/* 만료 건강 분석 기록 정리 Use Case 검증 */
package com.newsverification.healthrecord.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 현재 시각 기준 만료 기록 정리 동작 */
class ExpiredHealthRecordCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T03:10:00Z");

    /** 현재 시각 이하 기록 삭제와 처리 건수 반환 */
    @Test
    void deletesRecordsExpiredAtOrBeforeCurrentTime() {
        var store = new CapturingStore();
        var service = new ExpiredHealthRecordCleanupService(
                store,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        int deletedCount = service.cleanupExpiredRecords();

        assertThat(store.cutoff).isEqualTo(NOW);
        assertThat(deletedCount).isEqualTo(2);
    }

    /** 삭제 기준 확인용 Port 구현 */
    private static final class CapturingStore implements HealthRecordStore {

        private Instant cutoff;

        @Override
        public SavedRecord save(SaveCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult findAll(long userId, Instant activeAt, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteExpiredAtOrBefore(Instant cutoff) {
            this.cutoff = cutoff;
            return 2;
        }
    }
}
