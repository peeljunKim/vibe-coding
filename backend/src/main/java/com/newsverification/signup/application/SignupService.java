/* 회원가입 Application 경계 */
package com.newsverification.signup.application;

/** 일반 계정 생성과 이메일 인증 Use Case */
public interface SignupService {

    /** 미인증 일반 계정 생성과 인증번호 발급 */
    PendingSignup register(RegisterCommand command);

    /** 이메일 인증 완료와 계정 활성화 */
    CompletedSignup verifyEmail(VerifyCommand command);

    /** 기존 인증번호 무효화와 새 번호 발급 */
    PendingSignup resendVerification(long userId);

    /** 민감정보 문자열 변환을 차단한 일반 회원가입 입력 */
    final class RegisterCommand {
        private final String inviteCode;
        private final String username;
        private final String password;
        private final String passwordConfirm;
        private final String email;
        private final String phoneNumber;
        private final boolean agreementsAccepted;

        public RegisterCommand(
                String inviteCode,
                String username,
                String password,
                String passwordConfirm,
                String email,
                String phoneNumber,
                boolean agreementsAccepted
        ) {
            this.inviteCode = inviteCode;
            this.username = username;
            this.password = password;
            this.passwordConfirm = passwordConfirm;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.agreementsAccepted = agreementsAccepted;
        }

        public String inviteCode() {
            return inviteCode;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public String passwordConfirm() {
            return passwordConfirm;
        }

        public String email() {
            return email;
        }

        public String phoneNumber() {
            return phoneNumber;
        }

        public boolean agreementsAccepted() {
            return agreementsAccepted;
        }

        @Override
        public String toString() {
            return "RegisterCommand[redacted]";
        }
    }

    /** 인증번호 확인 입력 */
    record VerifyCommand(long userId, String code) {

        @Override
        public String toString() {
            return "VerifyCommand[redacted]";
        }
    }

    /** 이메일 인증 대기 정보 */
    record PendingSignup(
            long userId,
            String username,
            String email,
            int remainingAttempts,
            long resendAvailableInSeconds
    ) {

        @Override
        public String toString() {
            return "PendingSignup[userId=" + userId + ", redacted]";
        }
    }

    /** 활성화 완료 계정 정보 */
    record CompletedSignup(long userId, String username, String email) {

        @Override
        public String toString() {
            return "CompletedSignup[userId=" + userId + ", redacted]";
        }
    }
}
