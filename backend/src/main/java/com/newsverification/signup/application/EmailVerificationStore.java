/* 이메일 인증 상태 저장 Port */
package com.newsverification.signup.application;

/** 인증번호 만료·시도·재발송 제한 경계 */
public interface EmailVerificationStore {

    IssueResult issue(long userId, String code);

    VerificationResult verify(long userId, String code);

    void consume(long userId);

    record IssueResult(int remainingAttempts, long resendAvailableInSeconds) {
    }

    enum VerificationResult {
        VERIFIED,
        INVALID,
        EXPIRED,
        BLOCKED
    }
}
