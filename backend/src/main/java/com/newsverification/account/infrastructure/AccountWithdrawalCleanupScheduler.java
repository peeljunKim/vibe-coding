/* 탈퇴 계정 보관 만료 정리 일정 */
package com.newsverification.account.infrastructure;

import com.newsverification.account.application.AccountWithdrawalService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보관 기한이 지난 탈퇴 계정의 일일 정리 */
@Component
public class AccountWithdrawalCleanupScheduler {

    private final AccountWithdrawalService service;

    public AccountWithdrawalCleanupScheduler(AccountWithdrawalService service) {
        this.service = service;
    }

    /** 매일 새벽 탈퇴 계정 정리 */
    @Scheduled(
            cron = "${ACCOUNT_WITHDRAWAL_CLEANUP_CRON:0 30 3 * * *}",
            zone = "${app.time-zone:Asia/Seoul}"
    )
    public void cleanupExpiredAccounts() {
        int deleted;
        do {
            deleted = service.cleanupExpiredAccounts();
        } while (deleted > 0);
    }
}
