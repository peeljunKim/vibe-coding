/* 소셜 계정 영속 Port */
package com.newsverification.auth.application;

import java.util.Optional;

/** Provider 식별자와 내부 회원 연결 조회 */
public interface SocialAccountStore {

    Optional<Account> findByProviderAndSubject(String provider, String subject);

    boolean existsByEmail(String email);

    Account create(NewAccount newAccount);

    record NewAccount(
            String provider,
            String subject,
            String email,
            java.time.Instant verifiedAt
    ) {

        @Override
        public String toString() {
            return "NewAccount[provider=" + provider + ", redacted]";
        }
    }

    record Account(long userId, String role, boolean active) {
    }
}
