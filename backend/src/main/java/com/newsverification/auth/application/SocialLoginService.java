/* 소셜 로그인 Use Case */
package com.newsverification.auth.application;

/** Provider 인증 결과의 회원 로그인·가입 대기 처리 */
public interface SocialLoginService {

    LoginResolution resolve(ProviderIdentity identity);

    AuthenticatedAccount completeSignup(CompleteSignupCommand command);

    record ProviderIdentity(
            String provider,
            String subject,
            String email,
            boolean emailVerified
    ) {

        @Override
        public String toString() {
            return "ProviderIdentity[provider=" + provider + ", redacted]";
        }
    }

    record LoginResolution(
            boolean existingAccount,
            Long userId,
            String role,
            ProviderIdentity pendingIdentity
    ) {

        static LoginResolution existing(long userId, String role) {
            return new LoginResolution(true, userId, role, null);
        }
    }

    record CompleteSignupCommand(
            ProviderIdentity identity,
            String inviteCode,
            boolean agreementsAccepted
    ) {

        @Override
        public String toString() {
            return "CompleteSignupCommand[redacted]";
        }
    }

    record AuthenticatedAccount(long userId, String role) {
    }
}
