/* 소셜 로그인 기본 처리 */
package com.newsverification.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Provider 고유 식별자 기반 회원 확인 */
public class DefaultSocialLoginService implements SocialLoginService {

    private final List<String> inviteCodes;
    private final SocialAccountStore accountStore;
    private final Clock clock;

    public DefaultSocialLoginService(
            List<String> inviteCodes,
            SocialAccountStore accountStore,
            Clock clock
    ) {
        this.inviteCodes = List.copyOf(Objects.requireNonNull(inviteCodes));
        this.accountStore = Objects.requireNonNull(accountStore);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 기존 소셜 회원 확인 */
    @Override
    public LoginResolution resolve(ProviderIdentity identity) {
        if (identity == null || identity.provider() == null || identity.subject() == null) {
            throw new SocialLoginException("SOCIAL_IDENTITY_INVALID");
        }
        String provider = identity.provider().trim().toUpperCase(Locale.ROOT);
        String subject = identity.subject().trim();
        if (provider.isBlank() || subject.isBlank()) {
            throw new SocialLoginException("SOCIAL_IDENTITY_INVALID");
        }
        String email = normalizeVerifiedEmail(identity);
        SocialAccountStore.Account account = accountStore
                .findByProviderAndSubject(provider, subject)
                .orElse(null);
        if (account != null) {
            if (!account.active()) {
                throw new SocialLoginException("SOCIAL_ACCOUNT_NOT_ACTIVE");
            }
            return LoginResolution.existing(account.userId(), account.role());
        }
        if (accountStore.existsByEmail(email)) {
            throw new SocialLoginException("SOCIAL_EMAIL_ALREADY_REGISTERED");
        }
        return new LoginResolution(
                false,
                null,
                null,
                new ProviderIdentity(provider, subject, email, true)
        );
    }

    /** 초대 코드 확인과 소셜 계정 생성 */
    @Override
    public AuthenticatedAccount completeSignup(CompleteSignupCommand command) {
        requireValidInviteCode(command.inviteCode());
        if (!command.agreementsAccepted()) {
            throw new SocialLoginException("AGREEMENTS_REQUIRED");
        }
        ProviderIdentity identity = Objects.requireNonNull(command.identity());
        SocialAccountStore.Account account = accountStore.create(new SocialAccountStore.NewAccount(
                identity.provider(),
                identity.subject(),
                identity.email(),
                clock.instant()
        ));
        return new AuthenticatedAccount(account.userId(), account.role());
    }

    private String normalizeVerifiedEmail(ProviderIdentity identity) {
        String email = identity.email() == null
                ? ""
                : identity.email().trim().toLowerCase(Locale.ROOT);
        if (!identity.emailVerified() || email.isBlank()) {
            throw new SocialLoginException("SOCIAL_EMAIL_REQUIRED");
        }
        return email;
    }

    private void requireValidInviteCode(String inviteCode) {
        byte[] candidate = Objects.requireNonNullElse(inviteCode, "")
                .getBytes(StandardCharsets.UTF_8);
        boolean valid = false;
        for (String configuredCode : inviteCodes) {
            if (!configuredCode.isBlank()) {
                valid |= MessageDigest.isEqual(
                        candidate,
                        configuredCode.getBytes(StandardCharsets.UTF_8)
                );
            }
        }
        if (!valid) {
            throw new SocialLoginException("INVALID_INVITE_CODE");
        }
    }
}
