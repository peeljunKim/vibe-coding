/* 회원 탈퇴 수명주기 Application 정책 검증 */
package com.newsverification.account.application;

import com.newsverification.auth.application.AccountSessionInvalidator;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 탈퇴 신청·Session 만료·30일 뒤 삭제 검증 */
class AccountWithdrawalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private UserAccountRepository repository;
    private AccountSessionInvalidator sessionInvalidator;
    private AccountWithdrawalService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        sessionInvalidator = mock(AccountSessionInvalidator.class);
        service = new AccountWithdrawalService(
                repository,
                sessionInvalidator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /** ACTIVE 계정의 7일 복구와 신청 후 30일 삭제 예약 */
    @Test
    void requestsWithdrawalAndInvalidatesSessionsBeforeCommit() {
        UserAccount account = activeAccount(42L, "withdraw26");
        when(repository.findByIdForWithdrawal(42L)).thenReturn(Optional.of(account));

        TransactionSynchronizationManager.initSynchronization();
        try {
            AccountWithdrawalService.Withdrawal result = service.request("42");

            assertThat(result.recoveryDeadline()).isEqualTo(NOW.plusSeconds(7L * 24 * 60 * 60));
            assertThat(result.scheduledDeletionAt()).isEqualTo(NOW.plusSeconds(30L * 24 * 60 * 60));
            assertThat(account.withdrawalPending()).isTrue();
            verify(sessionInvalidator).invalidateAll(42L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Session 만료 실패 시 탈퇴 요청 성공 반환 차단 */
    @Test
    void failsWithdrawalBeforeCommitWhenSessionInvalidationFails() {
        UserAccount account = activeAccount(42L, "withdraw26");
        when(repository.findByIdForWithdrawal(42L)).thenReturn(Optional.of(account));
        doThrow(new IllegalStateException("session store unavailable"))
                .when(sessionInvalidator).invalidateAll(42L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.request("42"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("session store unavailable");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** ACTIVE가 아닌 계정의 중복 탈퇴 신청 차단 */
    @Test
    void rejectsAccountThatIsNotActive() {
        UserAccount account = activeAccount(42L, "withdraw26");
        account.requestWithdrawal(NOW, NOW.plusSeconds(30L * 24 * 60 * 60));
        when(repository.findByIdForWithdrawal(42L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.request("42"))
                .isInstanceOf(AccountWithdrawalException.class)
                .hasMessage("ACCOUNT_NOT_ACTIVE");
    }

    /** 탈퇴 신청 후 30일 보관이 끝난 계정만 한 묶음 정리 */
    @Test
    void deletesOnlyExpiredWithdrawalBatch() {
        UserAccount expired = activeAccount(42L, "expired26");
        expired.requestWithdrawal(NOW.minusSeconds(31L * 24 * 60 * 60), NOW.minusSeconds(24 * 60 * 60));
        when(repository.findTop100ByStatusAndScheduledDeletionAtLessThanEqualOrderByScheduledDeletionAtAscIdAsc(
                UserAccount.UserStatus.WITHDRAWAL_PENDING,
                NOW
        )).thenReturn(List.of(expired));

        int deleted = service.cleanupExpiredAccounts();

        assertThat(deleted).isEqualTo(1);
        verify(repository).deleteAll(List.of(expired));
        verify(repository).flush();
    }

    private UserAccount activeAccount(long id, String username) {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        UserAccount account = new UserAccount(
                username,
                encoder.encode("Password!23"),
                username + "@example.com",
                "01012345678",
                NOW.minusSeconds(120)
        );
        ReflectionTestUtils.setField(account, "id", id);
        account.activate(NOW.minusSeconds(60));
        return account;
    }
}
