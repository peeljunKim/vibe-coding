/* 일반 회원 로그인 상태 전환 검증 */
package com.newsverification.signup.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 연속 실패 잠금과 만료 후 초기화 검증 */
class UserAccountLoginTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");

    /** 다섯 번째 실패의 30분 잠금 전환 */
    @Test
    void locksAccountOnFifthConsecutiveFailure() {
        UserAccount account = account();

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(account.recordLoginFailure(NOW, 5, 1800)).isFalse();
        }
        assertThat(account.recordLoginFailure(NOW, 5, 1800)).isTrue();

        assertThat(account.failedLoginCount()).isEqualTo(5);
        assertThat(account.loginLockedUntil()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(account.loginLockedAt(NOW.plusSeconds(1799))).isTrue();
    }

    /** 잠금 만료 시 실패 상태 초기화 */
    @Test
    void clearsExpiredLoginLock() {
        UserAccount account = account();
        for (int attempt = 0; attempt < 5; attempt++) {
            account.recordLoginFailure(NOW, 5, 1800);
        }

        account.clearExpiredLoginLock(NOW.plusSeconds(1800));

        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.loginLockedUntil()).isNull();
    }

    private UserAccount account() {
        UserAccount account = new UserAccount(
                "health26",
                "{noop}Password!23",
                "user@example.com",
                "01012345678",
                NOW.minusSeconds(60)
        );
        account.activate(NOW.minusSeconds(30));
        return account;
    }
}
