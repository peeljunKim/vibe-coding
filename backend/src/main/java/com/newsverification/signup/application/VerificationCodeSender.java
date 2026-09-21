/* 회원가입 인증번호 발송 Port */
package com.newsverification.signup.application;

/** 외부 이메일 발송 교체 경계 */
public interface VerificationCodeSender {

    void sendSignupCode(String email, String code);
}
