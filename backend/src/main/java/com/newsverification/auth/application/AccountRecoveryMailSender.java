/* 계정 복구 이메일 발송 Port */
package com.newsverification.auth.application;

/** 인증번호와 전체 아이디 발송 경계 */
public interface AccountRecoveryMailSender {

    void sendVerificationCode(String email, AccountRecoveryVerificationStore.Purpose purpose, String code);

    void sendUsername(String email, String username);
}
