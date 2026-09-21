/* 이메일 인증번호 생성 Port */
package com.newsverification.signup.application;

/** 6자리 인증번호 생성 경계 */
public interface VerificationCodeGenerator {

    String generate();
}
