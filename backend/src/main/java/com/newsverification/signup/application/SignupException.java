/* 회원가입 업무 실패 */
package com.newsverification.signup.application;

/** 안정적인 회원가입 오류 코드 */
public class SignupException extends RuntimeException {

    public SignupException(String code) {
        super(code);
    }

    public String code() {
        return getMessage();
    }
}
