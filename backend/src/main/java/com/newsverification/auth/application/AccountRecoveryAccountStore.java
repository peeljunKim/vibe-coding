/* 계정 복구 사용자 저장 Port */
package com.newsverification.auth.application;

import java.util.Optional;

/** 활성 일반 계정 조회와 비밀번호 변경 경계 */
public interface AccountRecoveryAccountStore {

    Optional<Account> findActiveLocalByEmail(String email);

    void changePassword(long userId, String passwordHash);

    record Account(long userId, String username, String email) {
    }
}
