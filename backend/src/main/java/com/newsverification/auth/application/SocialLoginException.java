/* 소셜 로그인 업무 예외 */
package com.newsverification.auth.application;

/** 공개 오류 코드만 전달하는 소셜 인증 실패 */
public class SocialLoginException extends RuntimeException {

    private final String code;

    public SocialLoginException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
