/* 탈퇴 계정 정리 Scheduler 검증 */
package com.newsverification.account.infrastructure;

import com.newsverification.account.application.AccountWithdrawalService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 만료 계정 전체 묶음 정리 검증 */
class AccountWithdrawalCleanupSchedulerTest {

    /** 한 번의 일정에서 남은 묶음까지 반복 정리 */
    @Test
    void cleansAllExpiredAccountBatches() {
        AccountWithdrawalService service = mock(AccountWithdrawalService.class);
        when(service.cleanupExpiredAccounts()).thenReturn(100, 100, 0);
        AccountWithdrawalCleanupScheduler scheduler = new AccountWithdrawalCleanupScheduler(service);

        scheduler.cleanupExpiredAccounts();

        verify(service, org.mockito.Mockito.times(3)).cleanupExpiredAccounts();
    }
}
