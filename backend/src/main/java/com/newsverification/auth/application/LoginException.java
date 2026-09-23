/* 일반 로그인 업무 오류 */
package com.newsverification.auth.application;

/** 공개 오류 코드와 재시도 시간 전달 */
public class LoginException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;

    public LoginException(String code) {
        this(code, 0);
    }

    public LoginException(String code, long retryAfterSeconds) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
