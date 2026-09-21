/* 미인증 계정 정리 일정 실행 */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.PendingSignupCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 만료된 미인증 계정의 일일 정리 */
@Component
public class PendingSignupCleanupScheduler {

    private final PendingSignupCleanupService cleanupService;

    public PendingSignupCleanupScheduler(PendingSignupCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    /** 매일 새벽 만료 계정 정리 */
    @Scheduled(
            cron = "${SIGNUP_PENDING_CLEANUP_CRON:0 0 3 * * *}",
            zone = "${app.time-zone:Asia/Seoul}"
    )
    public void cleanupExpiredAccounts() {
        cleanupService.cleanupExpiredAccounts();
    }
}
