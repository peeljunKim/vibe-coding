/* 이메일 계정 복구 정책 검증 */
package com.newsverification.auth.application;

import com.newsverification.auth.application.AccountRecoveryVerificationStore.Purpose;
import com.newsverification.auth.application.AccountRecoveryVerificationStore.VerificationResult;
import com.newsverification.signup.application.VerificationCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 계정 존재 은닉과 인증 완료 변경 검증 */
class DefaultAccountRecoveryServiceTest {

    private final FakeAccountStore accounts = new FakeAccountStore();
    private final FakeVerificationStore verifications = new FakeVerificationStore();
    private final FakeMailSender mailSender = new FakeMailSender();
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final List<Long> invalidatedUsers = new ArrayList<>();
    private DefaultAccountRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAccountRecoveryService(
                accounts,
                verifications,
                mailSender,
                () -> "482916",
                passwordEncoder,
                invalidatedUsers::add,
                "test-account-recovery-key".getBytes(StandardCharsets.UTF_8)
        );
    }

    /** 존재하지 않는 이메일과 동일한 인증번호 요청 응답 */
    @Test
    void hidesUnknownEmailWhenRequestingUsernameCode() {
        AccountRecoveryService.RecoveryRequest result = service.requestUsernameCode("UNKNOWN@example.com");

        assertThat(result.remainingAttempts()).isEqualTo(5);
        assertThat(result.resendAvailableInSeconds()).isEqualTo(60);
        assertThat(verifications.issuedPurpose).isEqualTo(Purpose.USERNAME);
        assertThat(verifications.issuedCode).isEqualTo("482916");
        assertThat(mailSender.codeMessages).isEmpty();
    }

    /** 인증된 아이디 마스킹과 전체 아이디 이메일 발송 */
    @Test
    void returnsMaskedUsernameAndEmailsFullUsernameAfterVerification() {
        accounts.account = new AccountRecoveryAccountStore.Account(42L, "healthcheck26", "user@example.com");
        verifications.result = VerificationResult.VERIFIED;

        AccountRecoveryService.RecoveredUsername result = service.verifyUsernameCode(
                "USER@example.com",
                "482916"
        );

        assertThat(result.maskedUsername()).isEqualTo("he***********");
        assertThat(mailSender.usernames).containsExactly("user@example.com:healthcheck26");
        assertThat(verifications.consumedPurpose).isEqualTo(Purpose.USERNAME);
    }

    /** 새 비밀번호 Hash 저장과 기존 Session 전체 만료 */
    @Test
    void resetsPasswordAndInvalidatesExistingSessions() {
        accounts.account = new AccountRecoveryAccountStore.Account(42L, "healthcheck26", "user@example.com");
        verifications.result = VerificationResult.VERIFIED;

        service.resetPassword(new AccountRecoveryService.ResetPasswordCommand(
                "user@example.com",
                "482916",
                "ChangedPassword!23",
                "ChangedPassword!23"
        ));

        assertThat(invalidatedUsers).containsExactly(42L);
        assertThat(accounts.changedUserId).isEqualTo(42L);
        assertThat(passwordEncoder.matches("ChangedPassword!23", accounts.changedPasswordHash)).isTrue();
        assertThat(verifications.consumedPurpose).isEqualTo(Purpose.PASSWORD);
    }

    /** 잘못된 인증번호의 공통 오류 */
    @Test
    void rejectsInvalidVerificationCodeWithoutAccountDetails() {
        accounts.account = new AccountRecoveryAccountStore.Account(42L, "healthcheck26", "user@example.com");
        verifications.result = VerificationResult.INVALID;

        assertThatThrownBy(() -> service.verifyUsernameCode("user@example.com", "000000"))
                .isInstanceOf(AccountRecoveryException.class)
                .hasMessage("INVALID_VERIFICATION_CODE");
        assertThat(mailSender.usernames).isEmpty();
    }

    private static final class FakeAccountStore implements AccountRecoveryAccountStore {
        private Account account;
        private long changedUserId;
        private String changedPasswordHash;

        @Override
        public Optional<Account> findActiveLocalByEmail(String email) {
            return account != null && account.email().equals(email) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public void changePassword(long userId, String passwordHash) {
            changedUserId = userId;
            changedPasswordHash = passwordHash;
        }
    }

    private static final class FakeVerificationStore implements AccountRecoveryVerificationStore {
        private VerificationResult result = VerificationResult.EXPIRED;
        private Purpose issuedPurpose;
        private String issuedCode;
        private Purpose consumedPurpose;

        @Override
        public IssueResult issue(Purpose purpose, String lookupKey, String code) {
            issuedPurpose = purpose;
            issuedCode = code;
            return new IssueResult(5, 60);
        }

        @Override
        public VerificationResult verify(Purpose purpose, String lookupKey, String code) {
            return result;
        }

        @Override
        public void consume(Purpose purpose, String lookupKey) {
            consumedPurpose = purpose;
        }
    }

    private static final class FakeMailSender implements AccountRecoveryMailSender {
        private final List<String> codeMessages = new ArrayList<>();
        private final List<String> usernames = new ArrayList<>();

        @Override
        public void sendVerificationCode(String email, Purpose purpose, String code) {
            codeMessages.add(email + ":" + purpose + ":" + code);
        }

        @Override
        public void sendUsername(String email, String username) {
            usernames.add(email + ":" + username);
        }
    }
}
