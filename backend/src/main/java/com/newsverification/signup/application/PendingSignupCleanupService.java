/* 미인증 계정 만료 정리 Use Case */
package com.newsverification.signup.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** 가입 후 7일이 지난 미인증 계정 정리 */
public class PendingSignupCleanupService {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final SignupAccountStore accountStore;
    private final Clock clock;

    public PendingSignupCleanupService(SignupAccountStore accountStore, Clock clock) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 현재 시각 기준 만료 계정 정리 */
    public int cleanupExpiredAccounts() {
        return accountStore.deletePendingCreatedBefore(clock.instant().minus(RETENTION));
    }
}
