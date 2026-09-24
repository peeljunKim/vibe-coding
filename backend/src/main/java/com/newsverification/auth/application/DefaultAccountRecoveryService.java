/* 이메일 기반 계정 복구 처리 */
package com.newsverification.auth.application;

import com.newsverification.auth.application.AccountRecoveryVerificationStore.Purpose;
import com.newsverification.auth.application.AccountRecoveryVerificationStore.VerificationResult;
import com.newsverification.signup.application.VerificationCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/** 계정 존재 여부를 숨기는 이메일 복구 흐름 */
public class DefaultAccountRecoveryService implements AccountRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAccountRecoveryService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AccountRecoveryAccountStore accountStore;
    private final AccountRecoveryVerificationStore verificationStore;
    private final AccountRecoveryMailSender mailSender;
    private final Executor mailExecutor;
    private final VerificationCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final AccountSessionInvalidator sessionInvalidator;
    private final byte[] lookupHmacKey;

    public DefaultAccountRecoveryService(
            AccountRecoveryAccountStore accountStore,
            AccountRecoveryVerificationStore verificationStore,
            AccountRecoveryMailSender mailSender,
            Executor mailExecutor,
            VerificationCodeGenerator codeGenerator,
            PasswordEncoder passwordEncoder,
            AccountSessionInvalidator sessionInvalidator,
            byte[] lookupHmacKey
    ) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.verificationStore = Objects.requireNonNull(verificationStore);
        this.mailSender = Objects.requireNonNull(mailSender);
        this.mailExecutor = Objects.requireNonNull(mailExecutor);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.sessionInvalidator = Objects.requireNonNull(sessionInvalidator);
        this.lookupHmacKey = Objects.requireNonNull(lookupHmacKey).clone();
        if (this.lookupHmacKey.length == 0) {
            throw new IllegalArgumentException("Account recovery lookup HMAC key is required");
        }
    }

    /** 아이디 찾기 인증번호 발급 */
    @Override
    public RecoveryRequest requestUsernameCode(String email) {
        return requestCode(Purpose.USERNAME, email);
    }

    /** 인증 완료 아이디의 마스킹 반환과 전체 아이디 발송 */
    @Override
    public RecoveredUsername verifyUsernameCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String lookupKey = lookupKey(normalizedEmail);
        requireVerified(Purpose.USERNAME, lookupKey, code);
        AccountRecoveryAccountStore.Account account = accountStore.findActiveLocalByEmail(normalizedEmail)
                .orElseThrow(this::invalidCode);
        mailSender.sendUsername(account.email(), account.username());
        verificationStore.consume(Purpose.USERNAME, lookupKey);
        return new RecoveredUsername(maskUsername(account.username()));
    }

    /** 비밀번호 재설정 인증번호 발급 */
    @Override
    public RecoveryRequest requestPasswordCode(String email) {
        return requestCode(Purpose.PASSWORD, email);
    }

    /** 인증 완료 비밀번호 변경과 기존 Session 만료 */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        if (!command.password().equals(command.passwordConfirm())) {
            throw new AccountRecoveryException("PASSWORD_CONFIRMATION_MISMATCH");
        }
        String normalizedEmail = normalizeEmail(command.email());
        String lookupKey = lookupKey(normalizedEmail);
        requireVerified(Purpose.PASSWORD, lookupKey, command.code());
        AccountRecoveryAccountStore.Account account = accountStore.findActiveLocalByEmail(normalizedEmail)
                .orElseThrow(this::invalidCode);
        accountStore.changePassword(account.userId(), passwordEncoder.encode(command.password()));
        sessionInvalidator.invalidateAll(account.userId());
        verificationStore.consume(Purpose.PASSWORD, lookupKey);
    }

    private RecoveryRequest requestCode(Purpose purpose, String email) {
        String normalizedEmail = normalizeEmail(email);
        String lookupKey = lookupKey(normalizedEmail);
        String code = codeGenerator.generate();
        AccountRecoveryVerificationStore.IssueResult issue = verificationStore.issue(purpose, lookupKey, code);
        dispatchCodeMail(
                accountStore.findActiveLocalByEmail(normalizedEmail),
                purpose,
                code,
                lookupKey
        );
        return new RecoveryRequest(issue.remainingAttempts(), issue.resendAvailableInSeconds());
    }

    private void dispatchCodeMail(
            Optional<AccountRecoveryAccountStore.Account> account,
            Purpose purpose,
            String code,
            String lookupKey
    ) {
        Runnable delivery = () -> {
            try {
                account.ifPresent(existing -> mailSender.sendVerificationCode(existing.email(), purpose, code));
            } catch (RuntimeException exception) {
                compensateFailedDelivery(purpose, lookupKey, code, exception);
            }
        };
        try {
            mailExecutor.execute(delivery);
        } catch (RuntimeException exception) {
            compensateFailedDelivery(purpose, lookupKey, code, exception);
        }
    }

    private void compensateFailedDelivery(
            Purpose purpose,
            String lookupKey,
            String code,
            RuntimeException exception
    ) {
        verificationStore.consumeIfCodeMatches(purpose, lookupKey, code);
        LOGGER.error("Account recovery email delivery failed. purpose={} cause={}",
                purpose, exception.getClass().getSimpleName());
    }

    private void requireVerified(Purpose purpose, String lookupKey, String code) {
        VerificationResult result = verificationStore.verify(purpose, lookupKey, code);
        if (result == VerificationResult.VERIFIED) {
            return;
        }
        throw switch (result) {
            case INVALID -> invalidCode();
            case EXPIRED -> new AccountRecoveryException("VERIFICATION_EXPIRED");
            case BLOCKED -> new AccountRecoveryException("VERIFICATION_BLOCKED");
            case VERIFIED -> new IllegalStateException("Verified result is not a failure");
        };
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String lookupKey(String email) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(lookupHmacKey, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(("account-recovery:" + email)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Account recovery lookup key generation failed", exception);
        }
    }

    private String maskUsername(String username) {
        int visibleLength = Math.min(2, Math.max(1, username.length() - 1));
        return username.substring(0, visibleLength) + "*".repeat(username.length() - visibleLength);
    }

    private AccountRecoveryException invalidCode() {
        return new AccountRecoveryException("INVALID_VERIFICATION_CODE");
    }
}
