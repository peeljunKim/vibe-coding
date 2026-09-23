/* 일반 로그인 인증 처리 */
package com.newsverification.auth.application;

import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** 비밀번호 검증과 연속 실패 잠금 처리 */
@Service
public class DefaultLoginService implements LoginService {

    static final int MAXIMUM_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyPasswordHash;

    public DefaultLoginService(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("nonexistent-login-account");
    }

    /** 일반 계정 인증과 실패 상태 갱신 */
    @Override
    @Transactional(noRollbackFor = LoginException.class)
    public AuthenticatedAccount authenticate(LoginCommand command) {
        Instant now = clock.instant();
        UserAccount account = repository.findByUsernameForLogin(command.username())
                .orElse(null);

        if (account == null) {
            passwordEncoder.matches(command.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        account.clearExpiredLoginLock(now);
        boolean passwordMatches = account.localAccount()
                && account.passwordHash() != null
                && passwordEncoder.matches(command.password(), account.passwordHash());
        if (account.loginLockedAt(now)) {
            if (account.active() && passwordMatches) {
                throw locked(account.loginLockedUntil(), now);
            }
            throw invalidCredentials();
        }

        if (!account.active() || !passwordMatches) {
            account.recordLoginFailure(
                    now,
                    MAXIMUM_FAILURES,
                    LOCK_DURATION.toSeconds()
            );
            throw invalidCredentials();
        }

        account.resetLoginFailures();
        return new AuthenticatedAccount(account.id(), account.username(), account.role());
    }

    private LoginException invalidCredentials() {
        return new LoginException("INVALID_CREDENTIALS");
    }

    private LoginException locked(Instant lockedUntil, Instant now) {
        long retryAfterSeconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
        return new LoginException("LOGIN_LOCKED", retryAfterSeconds);
    }
}
