/* 일반 로그인 업무 오류 */
package com.newsverification.auth.application;

import java.time.Instant;

/** 공개 오류 코드와 재시도 시간 전달 */
public class LoginException extends RuntimeException {

    private final String code;
    private final long retryAfterSeconds;
    private final Instant recoveryDeadline;

    public LoginException(String code) {
        this(code, 0, null);
    }

    public LoginException(String code, long retryAfterSeconds) {
        this(code, retryAfterSeconds, null);
    }

    public LoginException(String code, Instant recoveryDeadline) {
        this(code, 0, recoveryDeadline);
    }

    private LoginException(String code, long retryAfterSeconds, Instant recoveryDeadline) {
        super(code);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.recoveryDeadline = recoveryDeadline;
    }

    public String code() {
        return code;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Instant recoveryDeadline() {
        return recoveryDeadline;
    }
}
