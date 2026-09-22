/* 일반 로그인 Application 정책 검증 */
package com.newsverification.auth.application;

import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 활성 계정 검증과 실패 횟수 처리 검증 */
class DefaultLoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T00:00:00Z");
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private UserAccountRepository repository;
    private DefaultLoginService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        service = new DefaultLoginService(
                repository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    /** 올바른 비밀번호의 인증과 실패 상태 초기화 */
    @Test
    void authenticatesActiveLocalAccountAndResetsFailures() {
        UserAccount account = activeAccount();
        account.recordLoginFailure(NOW.minusSeconds(60), 5, 1800);
        when(repository.findByUsernameForLogin("health26")).thenReturn(Optional.of(account));

        LoginService.AuthenticatedAccount authenticated = service.authenticate(
                new LoginService.LoginCommand("health26", "Password!23")
        );

        assertThat(authenticated.userId()).isEqualTo(42L);
        assertThat(authenticated.role()).isEqualTo("USER");
        assertThat(account.failedLoginCount()).isZero();
        assertThat(account.loginLockedUntil()).isNull();
    }

    /** 다섯 번째 잘못된 비밀번호의 잠금 오류 */
    @Test
    void locksAccountOnFifthInvalidPassword() {
        UserAccount account = activeAccount();
        for (int attempt = 0; attempt < 4; attempt++) {
            account.recordLoginFailure(NOW.minusSeconds(60), 5, 1800);
        }
        when(repository.findByUsernameForLogin("health26")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.authenticate(
                new LoginService.LoginCommand("health26", "WrongPassword!23")
        )).isInstanceOf(LoginException.class)
                .hasMessage("LOGIN_LOCKED")
                .extracting("retryAfterSeconds")
                .isEqualTo(1800L);
        assertThat(account.failedLoginCount()).isEqualTo(5);
    }

    /** 잠금 중 올바른 비밀번호의 인증 차단 */
    @Test
    void rejectsCorrectPasswordWhileLocked() {
        UserAccount account = activeAccount();
        for (int attempt = 0; attempt < 5; attempt++) {
            account.recordLoginFailure(NOW.minusSeconds(60), 5, 1800);
        }
        when(repository.findByUsernameForLogin("health26")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.authenticate(
                new LoginService.LoginCommand("health26", "Password!23")
        )).isInstanceOf(LoginException.class)
                .hasMessage("LOGIN_LOCKED");
    }

    /** 존재하지 않는 계정의 공통 인증 오류 */
    @Test
    void hidesWhetherAccountExists() {
        when(repository.findByUsernameForLogin("unknown26")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate(
                new LoginService.LoginCommand("unknown26", "Password!23")
        )).isInstanceOf(LoginException.class)
                .hasMessage("INVALID_CREDENTIALS");
    }

    private UserAccount activeAccount() {
        UserAccount account = new UserAccount(
                "health26",
                passwordEncoder.encode("Password!23"),
                "user@example.com",
                "01012345678",
                NOW.minusSeconds(120)
        );
        ReflectionTestUtils.setField(account, "id", 42L);
        account.activate(NOW.minusSeconds(60));
        return account;
    }
}
