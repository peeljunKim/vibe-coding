/* Native MySQL 일반 회원 계정 저장 통합 검증 */
package com.newsverification.signup.infrastructure;

import com.newsverification.NewsVerificationApplication;
import com.newsverification.account.application.AccountWithdrawalService;
import com.newsverification.auth.application.AccountSessionInvalidator;
import com.newsverification.auth.application.SocialAccountStore;
import com.newsverification.publisher.infrastructure.NativeMySqlTestConnectionGuard;
import com.newsverification.signup.application.SignupAccountStore;
import com.newsverification.signup.application.SignupException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** 실제 MySQL 계정 생성과 활성화 검증 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class SignupAccountStoreIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();

    @Autowired
    private SignupAccountStore accountStore;

    @Autowired
    private SocialAccountStore socialAccountStore;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 테스트 전용 DataSource 설정 */
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TEST_CONNECTION::databaseUrl);
        registry.add("spring.datasource.username", TEST_CONNECTION::username);
        registry.add("spring.datasource.password", TEST_CONNECTION::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    /** 미인증 계정 생성과 활성 상태 전환 검증 */
    @Test
    void createsPendingAccountAndActivatesIt() {
        Instant createdAt = Instant.parse("2026-09-21T00:00:00Z");
        SignupAccountStore.Account pending = accountStore.create(new SignupAccountStore.NewAccount(
                "signup26",
                "{bcrypt}$2a$10$not-a-plaintext-password-hash",
                "signup26@example.com",
                "01012345678",
                createdAt
        ));

        assertThat(pending.active()).isFalse();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, password_hash, phone_number FROM users WHERE id = ?",
                pending.id()
        )).containsEntry("status", "PENDING_EMAIL")
                .containsEntry("phone_number", "01012345678")
                .doesNotContainValue("Password!23");

        SignupAccountStore.Account active = accountStore.activate(
                pending.id(),
                createdAt.plusSeconds(60)
        );
        repository.flush();

        assertThat(active.active()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                pending.id()
        )).isEqualTo("ACTIVE");
    }

    /** 소셜 전용 계정과 Provider 식별자의 원자 저장 */
    @Test
    void createsActiveSocialAccountWithProviderIdentity() {
        Instant verifiedAt = Instant.parse("2026-10-05T00:00:00Z");

        SocialAccountStore.Account created = socialAccountStore.create(
                new SocialAccountStore.NewAccount(
                        "GOOGLE",
                        "google-native-it-subject",
                        "google-native-it@example.com",
                        verifiedAt
                )
        );
        repository.flush();

        assertThat(created.active()).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT account_type, status, username, password_hash, phone_number FROM users WHERE id = ?",
                created.userId()
        )).containsEntry("account_type", "SOCIAL")
                .containsEntry("status", "ACTIVE")
                .containsEntry("username", null)
                .containsEntry("password_hash", null)
                .containsEntry("phone_number", null);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT provider, provider_subject, provider_email, is_signup_identity "
                        + "FROM user_social_accounts WHERE user_id = ?",
                created.userId()
        )).containsEntry("provider", "GOOGLE")
                .containsEntry("provider_subject", "google-native-it-subject")
                .containsEntry("provider_email", "google-native-it@example.com")
                .containsEntry("is_signup_identity", true);
    }

    /** 사용자 아이디 중복 생성 차단 검증 */
    @Test
    void rejectsDuplicateUsername() {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        accountStore.create(new SignupAccountStore.NewAccount(
                "duplicate26",
                "{bcrypt}$2a$10$first-hash",
                "first26@example.com",
                "01011112222",
                now
        ));

        assertThatThrownBy(() -> accountStore.create(new SignupAccountStore.NewAccount(
                "duplicate26",
                "{bcrypt}$2a$10$second-hash",
                "second26@example.com",
                "01033334444",
                now
        ))).isInstanceOf(SignupException.class)
                .hasMessage("DUPLICATE_ACCOUNT");
    }

    /** 미인증 계정만 보상 삭제하는 경계 검증 */
    @Test
    void deletesOnlyPendingAccountForRegistrationCompensation() {
        SignupAccountStore.Account pending = accountStore.create(new SignupAccountStore.NewAccount(
                "cleanup26",
                "{bcrypt}$2a$10$cleanup-hash",
                "cleanup26@example.com",
                "01055556666",
                Instant.parse("2026-09-21T00:00:00Z")
        ));

        accountStore.deletePending(pending.id());
        repository.flush();

        assertThat(repository.findById(pending.id())).isEmpty();
    }

    /** 탈퇴 7일 복구와 신청 후 30일 보관 뒤 CASCADE 삭제 */
    @Test
    void recoversWithinSevenDaysAndDeletesAfterThirtyDayRetention() {
        Instant requestedAt = Instant.parse("2026-09-29T00:00:00Z");
        SignupAccountStore.Account created = accountStore.create(new SignupAccountStore.NewAccount(
                "withdraw26",
                "{bcrypt}$2a$10$withdrawal-hash",
                "withdraw26@example.com",
                "01077778888",
                requestedAt.minusSeconds(120)
        ));
        accountStore.activate(created.id(), requestedAt.minusSeconds(60));

        var sessionInvalidator = mock(AccountSessionInvalidator.class);
        var requestService = new AccountWithdrawalService(
                repository,
                sessionInvalidator,
                Clock.fixed(requestedAt, ZoneOffset.UTC)
        );
        AccountWithdrawalService.Withdrawal firstSchedule = requestService.request(Long.toString(created.id()));
        repository.flush();

        assertThat(firstSchedule.recoveryDeadline()).isEqualTo(requestedAt.plusSeconds(7L * 24 * 60 * 60));
        assertThat(firstSchedule.scheduledDeletionAt()).isEqualTo(requestedAt.plusSeconds(30L * 24 * 60 * 60));
        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, withdrawal_requested_at, scheduled_deletion_at FROM users WHERE id = ?",
                created.id()
        )).containsEntry("status", "WITHDRAWAL_PENDING");

        var account = repository.findByIdForWithdrawal(created.id()).orElseThrow();
        assertThat(account.recoverWithdrawal(requestedAt.plusSeconds(24 * 60 * 60))).isTrue();
        repository.flush();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT status, withdrawal_requested_at, scheduled_deletion_at FROM users WHERE id = ?",
                created.id()
        )).containsEntry("status", "ACTIVE")
                .containsEntry("withdrawal_requested_at", null)
                .containsEntry("scheduled_deletion_at", null);

        Instant secondRequestAt = requestedAt.plusSeconds(2L * 24 * 60 * 60);
        new AccountWithdrawalService(
                repository,
                sessionInvalidator,
                Clock.fixed(secondRequestAt, ZoneOffset.UTC)
        ).request(Long.toString(created.id()));
        jdbcTemplate.update("""
                INSERT INTO user_social_accounts (
                    user_id, provider, provider_subject, provider_email, is_signup_identity
                ) VALUES (?, 'GOOGLE', ?, ?, FALSE)
                """, created.id(), "withdraw-subject-" + created.id(), "linked-withdraw@example.com");

        int earlyDeleted = new AccountWithdrawalService(
                repository,
                sessionInvalidator,
                Clock.fixed(secondRequestAt.plusSeconds(30L * 24 * 60 * 60).minusSeconds(1), ZoneOffset.UTC)
        ).cleanupExpiredAccounts();
        int deleted = new AccountWithdrawalService(
                repository,
                sessionInvalidator,
                Clock.fixed(secondRequestAt.plusSeconds(30L * 24 * 60 * 60), ZoneOffset.UTC)
        ).cleanupExpiredAccounts();

        assertThat(earlyDeleted).isZero();
        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(created.id())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_social_accounts WHERE user_id = ?",
                Integer.class,
                created.id()
        )).isZero();
    }
}
