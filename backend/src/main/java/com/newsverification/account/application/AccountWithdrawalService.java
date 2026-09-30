/* 회원 탈퇴 수명주기 Use Case */
package com.newsverification.account.application;

import com.newsverification.auth.application.AccountSessionInvalidator;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 탈퇴 신청·Session 만료·보관 기한 삭제 처리 */
@Service
public class AccountWithdrawalService {

    private static final Duration RECOVERY_PERIOD = Duration.ofDays(7);
    private static final Duration RETENTION_PERIOD = Duration.ofDays(30);

    private final UserAccountRepository repository;
    private final AccountSessionInvalidator sessionInvalidator;
    private final Clock clock;

    public AccountWithdrawalService(
            UserAccountRepository repository,
            AccountSessionInvalidator sessionInvalidator,
            Clock clock
    ) {
        this.repository = repository;
        this.sessionInvalidator = sessionInvalidator;
        this.clock = clock;
    }

    /** 로그인 회원의 탈퇴 유예와 보관 시작 */
    @Transactional
    public Withdrawal request(String memberId) {
        long userId = parseMemberId(memberId);
        UserAccount account = repository.findByIdForWithdrawal(userId)
                .orElseThrow(() -> new AccountWithdrawalException("ACCOUNT_NOT_ACTIVE"));
        if (!account.active()) {
            throw new AccountWithdrawalException("ACCOUNT_NOT_ACTIVE");
        }

        Instant requestedAt = clock.instant();
        Instant recoveryDeadline = requestedAt.plus(RECOVERY_PERIOD);
        Instant scheduledDeletionAt = requestedAt.plus(RETENTION_PERIOD);
        account.requestWithdrawal(requestedAt, scheduledDeletionAt);
        invalidateSessionsAfterCommit(userId);
        return new Withdrawal(recoveryDeadline, scheduledDeletionAt);
    }

    /** 보관 기한이 지난 탈퇴 계정 정리 */
    @Transactional
    public int cleanupExpiredAccounts() {
        List<UserAccount> accounts = repository
                .findTop100ByStatusAndScheduledDeletionAtLessThanEqualOrderByScheduledDeletionAtAscIdAsc(
                        UserAccount.UserStatus.WITHDRAWAL_PENDING,
                        clock.instant()
                );
        repository.deleteAll(accounts);
        repository.flush();
        return accounts.size();
    }

    private long parseMemberId(String memberId) {
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException exception) {
            throw new AccountWithdrawalException("ACCOUNT_NOT_ACTIVE");
        }
    }

    /** 탈퇴 상태 Commit 뒤 기존 Session 만료 */
    private void invalidateSessionsAfterCommit(long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sessionInvalidator.invalidateAll(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sessionInvalidator.invalidateAll(userId);
            }
        });
    }

    /** 탈퇴 신청 결과 */
    public record Withdrawal(Instant recoveryDeadline, Instant scheduledDeletionAt) {
    }
}
