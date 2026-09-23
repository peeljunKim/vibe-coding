/* 계정 복구 업무 오류 */
package com.newsverification.auth.application;

/** 공개 오류 코드 기반 계정 복구 예외 */
public class AccountRecoveryException extends RuntimeException {

    private final String code;

    public AccountRecoveryException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
