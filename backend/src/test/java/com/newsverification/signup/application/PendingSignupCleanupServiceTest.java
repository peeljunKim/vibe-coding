/* 미인증 계정 정리 기준 검증 */
package com.newsverification.signup.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 가입 후 7일이 지난 미인증 계정 정리 검증 */
class PendingSignupCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    /** 현재 시각 기준 7일 이전 계정 정리 */
    @Test
    void deletesPendingAccountsCreatedBeforeSevenDayCutoff() {
        var accountStore = new RecordingAccountStore();
        var service = new PendingSignupCleanupService(
                accountStore,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        int deleted = service.cleanupExpiredAccounts();

        assertThat(accountStore.cutoff).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(deleted).isEqualTo(3);
    }

    private static final class RecordingAccountStore implements SignupAccountStore {
        private Instant cutoff;

        @Override
        public Account create(NewAccount newAccount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Account> findById(long userId) {
            return Optional.empty();
        }

        @Override
        public Account activate(long userId, Instant verifiedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deletePending(long userId) {
        }

        @Override
        public int deletePendingCreatedBefore(Instant cutoff) {
            this.cutoff = cutoff;
            return 3;
        }
    }
}
