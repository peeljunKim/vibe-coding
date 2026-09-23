/* 일반 로그인 유스케이스 계약 */
package com.newsverification.auth.application;

/** 일반 계정 인증과 잠금 정책 경계 */
public interface LoginService {

    AuthenticatedAccount authenticate(LoginCommand command);

    record LoginCommand(String username, String password) {

        @Override
        public String toString() {
            return "LoginCommand[username=[REDACTED], password=[REDACTED]]";
        }
    }

    record AuthenticatedAccount(long userId, String username, String role) {
    }
}
