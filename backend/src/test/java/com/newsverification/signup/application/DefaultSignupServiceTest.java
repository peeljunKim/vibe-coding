/* 회원가입 Application 흐름 검증 */
package com.newsverification.signup.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 초대 검증부터 계정 활성화까지의 공개 Use Case 검증 */
class DefaultSignupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    /** 유효한 입력의 미인증 계정과 인증번호 발급 */
    @Test
    void registersPendingAccountAndIssuesVerificationCode() {
        var accounts = new FakeAccountStore();
        var verifications = new FakeVerificationStore();
        var sender = new FakeCodeSender();
        var service = service(accounts, verifications, sender);

        SignupService.PendingSignup pending = service.register(command("INVITE-2026"));

        assertThat(pending.userId()).isEqualTo(42L);
        assertThat(accounts.saved.passwordHash()).startsWith("{bcrypt}");
        assertThat(verifications.issuedCode).isEqualTo("482916");
        assertThat(sender.email).isEqualTo("user@example.com");
        assertThat(sender.code).isEqualTo("482916");
        assertThat(command("INVITE-2026").toString())
                .doesNotContain("INVITE-2026", "Password!23", "user@example.com");
    }

    /** 잘못된 초대 코드의 계정 생성 차단 */
    @Test
    void rejectsInvalidInviteCodeBeforeSavingAccount() {
        var accounts = new FakeAccountStore();
        var service = service(accounts, new FakeVerificationStore(), new FakeCodeSender());

        assertThatThrownBy(() -> service.register(command("WRONG")))
                .isInstanceOf(SignupException.class)
                .hasMessage("INVALID_INVITE_CODE");
        assertThat(accounts.saved).isNull();
    }

    /** Redis 발급 실패 시 미인증 계정 보상 정리 */
    @Test
    void deletesPendingAccountWhenVerificationIssueFails() {
        var accounts = new FakeAccountStore();
        var verifications = new FakeVerificationStore();
        verifications.issueFailure = new IllegalStateException("redis unavailable");
        var service = service(accounts, verifications, new FakeCodeSender());

        assertThatThrownBy(() -> service.register(command("INVITE-2026")))
                .isSameAs(verifications.issueFailure);
        assertThat(accounts.deletedUserId).isEqualTo(42L);
        assertThat(verifications.consumed).isTrue();
    }

    /** 올바른 인증번호의 계정 활성화 */
    @Test
    void activatesAccountAfterVerification() {
        var accounts = new FakeAccountStore();
        accounts.account = new SignupAccountStore.Account(42L, "health26", "user@example.com", false);
        var verifications = new FakeVerificationStore();
        verifications.result = EmailVerificationStore.VerificationResult.VERIFIED;
        var service = service(accounts, verifications, new FakeCodeSender());

        SignupService.CompletedSignup completed = service.verifyEmail(
                new SignupService.VerifyCommand(42L, "482916")
        );

        assertThat(completed.username()).isEqualTo("health26");
        assertThat(accounts.activatedAt).isEqualTo(NOW);
        assertThat(verifications.consumed).isTrue();
    }

    /** 계정 활성화 후 Redis 정리 실패의 가입 완료 유지 */
    @Test
    void completesVerificationWhenCodeCleanupFailsAfterActivation() {
        var accounts = new FakeAccountStore();
        accounts.account = new SignupAccountStore.Account(42L, "health26", "user@example.com", false);
        var verifications = new FakeVerificationStore();
        verifications.result = EmailVerificationStore.VerificationResult.VERIFIED;
        verifications.consumeFailure = new IllegalStateException("redis unavailable");
        var service = service(accounts, verifications, new FakeCodeSender());

        SignupService.CompletedSignup completed = service.verifyEmail(
                new SignupService.VerifyCommand(42L, "482916")
        );

        assertThat(completed.userId()).isEqualTo(42L);
        assertThat(accounts.account.active()).isTrue();
    }

    /** 활성 계정의 인증 재호출 개인정보 반환 차단 */
    @Test
    void rejectsVerificationReplayForActiveAccount() {
        var accounts = new FakeAccountStore();
        accounts.account = new SignupAccountStore.Account(42L, "health26", "user@example.com", true);
        var service = service(accounts, new FakeVerificationStore(), new FakeCodeSender());

        assertThatThrownBy(() -> service.verifyEmail(
                new SignupService.VerifyCommand(42L, "482916")
        )).isInstanceOf(SignupException.class)
                .hasMessage("SIGNUP_NOT_FOUND");
    }

    /** 인증번호 Command의 문자열 노출 차단 */
    @Test
    void redactsVerificationCodeFromStringRepresentation() {
        assertThat(new SignupService.VerifyCommand(42L, "482916").toString())
                .doesNotContain("482916");
    }

    private DefaultSignupService service(
            FakeAccountStore accounts,
            FakeVerificationStore verifications,
            FakeCodeSender sender
    ) {
        return new DefaultSignupService(
                List.of("INVITE-2026", "SECOND-INVITE"),
                accounts,
                verifications,
                sender,
                () -> "482916",
                PasswordEncoderFactories.createDelegatingPasswordEncoder(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private SignupService.RegisterCommand command(String inviteCode) {
        return new SignupService.RegisterCommand(
                inviteCode,
                "health26",
                "Password!23",
                "Password!23",
                "user@example.com",
                "010-1234-5678",
                true
        );
    }

    private static final class FakeAccountStore implements SignupAccountStore {
        private NewAccount saved;
        private Account account;
        private Instant activatedAt;
        private Long deletedUserId;

        @Override
        public Account create(NewAccount newAccount) {
            saved = newAccount;
            account = new Account(42L, newAccount.username(), newAccount.email(), false);
            return account;
        }

        @Override
        public Optional<Account> findById(long userId) {
            return Optional.ofNullable(account);
        }

        @Override
        public Account activate(long userId, Instant verifiedAt) {
            activatedAt = verifiedAt;
            account = new Account(userId, account.username(), account.email(), true);
            return account;
        }

        @Override
        public void deletePending(long userId) {
            deletedUserId = userId;
            account = null;
        }

        @Override
        public int deletePendingCreatedBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class FakeVerificationStore implements EmailVerificationStore {
        private String issuedCode;
        private VerificationResult result = VerificationResult.VERIFIED;
        private boolean consumed;
        private RuntimeException issueFailure;
        private RuntimeException consumeFailure;

        @Override
        public IssueResult issue(long userId, String code) {
            if (issueFailure != null) {
                throw issueFailure;
            }
            issuedCode = code;
            return new IssueResult(5, 60);
        }

        @Override
        public VerificationResult verify(long userId, String code) {
            return result;
        }

        @Override
        public void consume(long userId) {
            if (consumeFailure != null) {
                throw consumeFailure;
            }
            consumed = true;
        }
    }

    private static final class FakeCodeSender implements VerificationCodeSender {
        private String email;
        private String code;

        @Override
        public void sendSignupCode(String email, String code) {
            this.email = email;
            this.code = code;
        }
    }
}
