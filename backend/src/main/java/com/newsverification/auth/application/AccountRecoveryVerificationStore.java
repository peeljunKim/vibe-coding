/* 계정 복구 인증 상태 저장 Port */
package com.newsverification.auth.application;

/** 인증번호 만료·시도·재발송 제한 경계 */
public interface AccountRecoveryVerificationStore {

    IssueResult issue(Purpose purpose, String lookupKey, String code);

    VerificationResult verify(Purpose purpose, String lookupKey, String code);

    void consume(Purpose purpose, String lookupKey);

    void consumeIfCodeMatches(Purpose purpose, String lookupKey, String code);

    enum Purpose {
        USERNAME,
        PASSWORD
    }

    record IssueResult(int remainingAttempts, long resendAvailableInSeconds) {
    }

    enum VerificationResult {
        VERIFIED,
        INVALID,
        EXPIRED,
        BLOCKED
    }
}
