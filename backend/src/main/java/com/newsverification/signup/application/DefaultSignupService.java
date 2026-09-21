/* 일반 회원가입 Use Case */
package com.newsverification.signup.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 초대 검증과 이메일 인증 기반 계정 활성화 */
public class DefaultSignupService implements SignupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSignupService.class);

    private final List<String> inviteCodes;
    private final SignupAccountStore accountStore;
    private final EmailVerificationStore verificationStore;
    private final VerificationCodeSender codeSender;
    private final VerificationCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DefaultSignupService(
            List<String> inviteCodes,
            SignupAccountStore accountStore,
            EmailVerificationStore verificationStore,
            VerificationCodeSender codeSender,
            VerificationCodeGenerator codeGenerator,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.inviteCodes = List.copyOf(Objects.requireNonNull(inviteCodes));
        this.accountStore = Objects.requireNonNull(accountStore);
        this.verificationStore = Objects.requireNonNull(verificationStore);
        this.codeSender = Objects.requireNonNull(codeSender);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 초대 검증과 미인증 계정 생성 */
    @Override
    public PendingSignup register(RegisterCommand command) {
        requireValidInviteCode(command.inviteCode());
        if (!command.password().equals(command.passwordConfirm())) {
            throw new SignupException("PASSWORD_CONFIRMATION_MISMATCH");
        }
        if (!command.agreementsAccepted()) {
            throw new SignupException("AGREEMENTS_REQUIRED");
        }

        SignupAccountStore.Account account = accountStore.create(new SignupAccountStore.NewAccount(
                command.username(),
                passwordEncoder.encode(command.password()),
                command.email().trim().toLowerCase(Locale.ROOT),
                command.phoneNumber().replace("-", ""),
                clock.instant()
        ));
        try {
            return issue(account);
        } catch (RuntimeException exception) {
            compensateFailedRegistration(account.id(), exception);
            throw exception;
        }
    }

    /** 인증번호 확인과 계정 활성화 */
    @Override
    public CompletedSignup verifyEmail(VerifyCommand command) {
        SignupAccountStore.Account account = accountStore.findById(command.userId())
                .orElseThrow(() -> new SignupException("SIGNUP_NOT_FOUND"));
        if (account.active()) {
            throw new SignupException("SIGNUP_NOT_FOUND");
        }
        EmailVerificationStore.VerificationResult result = verificationStore.verify(
                command.userId(),
                command.code()
        );
        if (result != EmailVerificationStore.VerificationResult.VERIFIED) {
            throw verificationFailure(result);
        }
        account = accountStore.activate(command.userId(), clock.instant());
        consumeAfterActivation(command.userId());
        return new CompletedSignup(account.id(), account.username(), account.email());
    }

    /** 인증번호 재발급 */
    @Override
    public PendingSignup resendVerification(long userId) {
        SignupAccountStore.Account account = accountStore.findById(userId)
                .orElseThrow(() -> new SignupException("SIGNUP_NOT_FOUND"));
        if (account.active()) {
            throw new SignupException("SIGNUP_NOT_FOUND");
        }
        return issue(account);
    }

    private PendingSignup issue(SignupAccountStore.Account account) {
        String code = codeGenerator.generate();
        EmailVerificationStore.IssueResult issue = verificationStore.issue(account.id(), code);
        codeSender.sendSignupCode(account.email(), code);
        return new PendingSignup(
                account.id(),
                account.username(),
                account.email(),
                issue.remainingAttempts(),
                issue.resendAvailableInSeconds()
        );
    }

    private void requireValidInviteCode(String receivedCode) {
        byte[] received = receivedCode.getBytes(StandardCharsets.UTF_8);
        boolean matches = false;
        for (String configuredCode : inviteCodes) {
            matches |= !configuredCode.isBlank() && MessageDigest.isEqual(
                    configuredCode.getBytes(StandardCharsets.UTF_8),
                    received
            );
        }
        if (!matches) {
            throw new SignupException("INVALID_INVITE_CODE");
        }
    }

    private SignupException verificationFailure(EmailVerificationStore.VerificationResult result) {
        return switch (result) {
            case INVALID -> new SignupException("INVALID_VERIFICATION_CODE");
            case EXPIRED -> new SignupException("VERIFICATION_EXPIRED");
            case BLOCKED -> new SignupException("VERIFICATION_BLOCKED");
            case VERIFIED -> throw new IllegalStateException("Verified result is not a failure");
        };
    }

    private void compensateFailedRegistration(long userId, RuntimeException originalFailure) {
        try {
            accountStore.deletePending(userId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        try {
            verificationStore.consume(userId);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void consumeAfterActivation(long userId) {
        try {
            verificationStore.consume(userId);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Email verification state cleanup failed after account activation. userId={} cause={}",
                    userId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
