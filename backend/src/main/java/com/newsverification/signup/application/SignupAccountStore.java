/* 회원 계정 영속 Port */
package com.newsverification.signup.application;

import java.time.Instant;
import java.util.Optional;

/** 미인증 계정 생성과 활성화 경계 */
public interface SignupAccountStore {

    Account create(NewAccount newAccount);

    Optional<Account> findById(long userId);

    Account activate(long userId, Instant verifiedAt);

    void deletePending(long userId);

    record NewAccount(
            String username,
            String passwordHash,
            String email,
            String phoneNumber,
            Instant inviteCodeVerifiedAt
    ) {

        @Override
        public String toString() {
            return "NewAccount[redacted]";
        }
    }

    record Account(long id, String username, String email, boolean active) {

        @Override
        public String toString() {
            return "Account[id=" + id + ", active=" + active + ", redacted]";
        }
    }
}
