/* 소셜 로그인 Application 경계 검증 */
package com.newsverification.auth.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Provider 고유 식별자 기반 로그인과 가입 대기 검증 */
class DefaultSocialLoginServiceTest {

    /** 기존 Google 회원의 내부 사용자 식별자 반환 */
    @Test
    void resolvesExistingGoogleAccountByProviderSubject() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        when(store.findByProviderAndSubject("GOOGLE", "google-subject-42"))
                .thenReturn(Optional.of(new SocialAccountStore.Account(42L, "USER", true)));
        var service = new DefaultSocialLoginService(
                List.of("INVITE-2026"),
                store,
                Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC)
        );

        SocialLoginService.LoginResolution resolution = service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "GOOGLE",
                        "google-subject-42",
                        "user@example.com",
                        true
                )
        );

        assertThat(resolution.existingAccount()).isTrue();
        assertThat(resolution.userId()).isEqualTo(42L);
        assertThat(resolution.role()).isEqualTo("USER");
    }

    /** 신규 Google 사용자의 최소 가입 정보 보관 */
    @Test
    void preparesVerifiedGoogleIdentityForInviteSignup() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        when(store.findByProviderAndSubject("GOOGLE", "new-google-subject"))
                .thenReturn(Optional.empty());
        when(store.existsByEmail("user@example.com")).thenReturn(false);
        var service = service(store);

        SocialLoginService.LoginResolution resolution = service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "google",
                        "new-google-subject",
                        "User@Example.com ",
                        true
                )
        );

        assertThat(resolution.existingAccount()).isFalse();
        assertThat(resolution.pendingIdentity().provider()).isEqualTo("GOOGLE");
        assertThat(resolution.pendingIdentity().email()).isEqualTo("user@example.com");
    }

    /** Provider 확인 이메일 누락 차단 */
    @Test
    void rejectsIdentityWithoutVerifiedEmail() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        var service = service(store);

        assertThatThrownBy(() -> service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "GOOGLE",
                        "new-google-subject",
                        "user@example.com",
                        false
                )
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("SOCIAL_EMAIL_REQUIRED");
    }

    /** Provider 고유 식별자 누락 차단 */
    @Test
    void rejectsIdentityWithoutProviderSubject() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        var service = service(store);

        assertThatThrownBy(() -> service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "GOOGLE",
                        " ",
                        "user@example.com",
                        true
                )
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("SOCIAL_IDENTITY_INVALID");
    }

    /** 기존 이메일 계정의 자동 병합 차단 */
    @Test
    void rejectsNewIdentityWhenEmailAlreadyBelongsToAnAccount() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        when(store.findByProviderAndSubject("GOOGLE", "new-google-subject"))
                .thenReturn(Optional.empty());
        when(store.existsByEmail("user@example.com")).thenReturn(true);
        var service = service(store);

        assertThatThrownBy(() -> service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "GOOGLE",
                        "new-google-subject",
                        "user@example.com",
                        true
                )
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("SOCIAL_EMAIL_ALREADY_REGISTERED");
    }

    /** 비활성 소셜 계정 로그인 차단 */
    @Test
    void rejectsInactiveSocialAccount() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        when(store.findByProviderAndSubject("GOOGLE", "withdrawn-google-subject"))
                .thenReturn(Optional.of(new SocialAccountStore.Account(42L, "USER", false)));
        var service = service(store);

        assertThatThrownBy(() -> service.resolve(
                new SocialLoginService.ProviderIdentity(
                        "GOOGLE",
                        "withdrawn-google-subject",
                        "user@example.com",
                        true
                )
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("SOCIAL_ACCOUNT_NOT_ACTIVE");
    }

    /** 초대 코드 확인 뒤 소셜 전용 계정 생성 */
    @Test
    void completesInviteOnlySocialSignup() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        when(store.create(any())).thenReturn(new SocialAccountStore.Account(43L, "USER", true));
        var service = service(store);
        var identity = new SocialLoginService.ProviderIdentity(
                "GOOGLE",
                "new-google-subject",
                "user@example.com",
                true
        );

        SocialLoginService.AuthenticatedAccount account = service.completeSignup(
                new SocialLoginService.CompleteSignupCommand(identity, "INVITE-2026", true)
        );

        assertThat(account.userId()).isEqualTo(43L);
        assertThat(account.role()).isEqualTo("USER");
        verify(store).create(new SocialAccountStore.NewAccount(
                "GOOGLE",
                "new-google-subject",
                "user@example.com",
                Instant.parse("2026-10-05T00:00:00Z")
        ));
    }

    /** 유효하지 않은 초대 코드의 가입 거절 */
    @Test
    void rejectsSocialSignupWithInvalidInviteCode() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        var service = service(store);
        var identity = new SocialLoginService.ProviderIdentity(
                "GOOGLE",
                "new-google-subject",
                "user@example.com",
                true
        );

        assertThatThrownBy(() -> service.completeSignup(
                new SocialLoginService.CompleteSignupCommand(identity, "INVALID", true)
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("INVALID_INVITE_CODE");
    }

    /** 필수 약관 미동의 소셜 가입 거절 */
    @Test
    void rejectsSocialSignupWithoutAgreement() {
        SocialAccountStore store = mock(SocialAccountStore.class);
        var service = service(store);
        var identity = new SocialLoginService.ProviderIdentity(
                "GOOGLE",
                "new-google-subject",
                "user@example.com",
                true
        );

        assertThatThrownBy(() -> service.completeSignup(
                new SocialLoginService.CompleteSignupCommand(identity, "INVITE-2026", false)
        ))
                .isInstanceOf(SocialLoginException.class)
                .extracting(exception -> ((SocialLoginException) exception).code())
                .isEqualTo("AGREEMENTS_REQUIRED");
    }

    private DefaultSocialLoginService service(SocialAccountStore store) {
        return new DefaultSocialLoginService(
                List.of("INVITE-2026"),
                store,
                Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC)
        );
    }
}
