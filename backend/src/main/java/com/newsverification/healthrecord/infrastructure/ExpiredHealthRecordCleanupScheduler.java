/* 만료 건강 분석 기록 정리 일정 실행 */
package com.newsverification.healthrecord.infrastructure;

import com.newsverification.healthrecord.application.ExpiredHealthRecordCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 건강 분석 저장 기록의 일일 정리 */
@Component
public class ExpiredHealthRecordCleanupScheduler {

    private final ExpiredHealthRecordCleanupService cleanupService;

    public ExpiredHealthRecordCleanupScheduler(ExpiredHealthRecordCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /** 매일 새벽 만료 기록 정리 */
    @Scheduled(
            cron = "${HEALTH_RECORD_CLEANUP_CRON:0 10 3 * * *}",
            zone = "${app.time-zone:Asia/Seoul}"
    )
    public void cleanupExpiredRecords() {
        cleanupService.cleanupExpiredRecords();
    }
}
