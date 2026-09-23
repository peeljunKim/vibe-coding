/* 이메일 기반 계정 복구 계약 */
package com.newsverification.auth.application;

/** 아이디 찾기와 비밀번호 재설정 Use Case */
public interface AccountRecoveryService {

    RecoveryRequest requestUsernameCode(String email);

    RecoveredUsername verifyUsernameCode(String email, String code);

    RecoveryRequest requestPasswordCode(String email);

    void resetPassword(ResetPasswordCommand command);

    record RecoveryRequest(int remainingAttempts, long resendAvailableInSeconds) {
    }

    record RecoveredUsername(String maskedUsername) {
    }

    final class ResetPasswordCommand {
        private final String email;
        private final String code;
        private final String password;
        private final String passwordConfirm;

        public ResetPasswordCommand(String email, String code, String password, String passwordConfirm) {
            this.email = email;
            this.code = code;
            this.password = password;
            this.passwordConfirm = passwordConfirm;
        }

        public String email() {
            return email;
        }

        public String code() {
            return code;
        }

        public String password() {
            return password;
        }

        public String passwordConfirm() {
            return passwordConfirm;
        }

        @Override
        public String toString() {
            return "ResetPasswordCommand[redacted]";
        }
    }
}
