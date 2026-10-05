/* 소셜 가입 대기 Session 값 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginService;

import java.io.Serial;
import java.io.Serializable;

/** Provider 인증 후 초대 코드 확인 전 최소 신원 */
public record PendingSocialSignup(
        String provider,
        String subject,
        String email
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialSignup";

    @Serial
    private static final long serialVersionUID = 1L;

    static PendingSocialSignup from(SocialLoginService.ProviderIdentity identity) {
        return new PendingSocialSignup(identity.provider(), identity.subject(), identity.email());
    }

    SocialLoginService.ProviderIdentity toIdentity() {
        return new SocialLoginService.ProviderIdentity(provider, subject, email, true);
    }

    @Override
    public String toString() {
        return "PendingSocialSignup[provider=" + provider + ", redacted]";
    }
}
