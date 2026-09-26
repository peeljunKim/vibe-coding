/* 만료 건강 분석 기록 정리 Use Case */
package com.newsverification.healthrecord.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/** 만료된 건강 분석 저장 기록 정리 */
@Service
public class ExpiredHealthRecordCleanupService {

    private final HealthRecordStore store;
    private final Clock clock;

    public ExpiredHealthRecordCleanupService(HealthRecordStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 현재 시각 기준 만료 기록 정리 */
    @Transactional
    public int cleanupExpiredRecords() {
        return store.deleteExpiredAtOrBefore(clock.instant());
    }
}
