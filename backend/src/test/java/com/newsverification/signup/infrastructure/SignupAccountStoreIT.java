/* Native MySQL 일반 회원 계정 저장 통합 검증 */
package com.newsverification.signup.infrastructure;

import com.newsverification.NewsVerificationApplication;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL 계정 생성과 활성화 검증 */
@SpringBootTest(classes = NewsVerificationApplication.class)
@Transactional
class SignupAccountStoreIT {

    private static final NativeMySqlTestConnectionGuard.Settings TEST_CONNECTION =
            NativeMySqlTestConnectionGuard.fromEnvironment();

    @Autowired
    private SignupAccountStore accountStore;

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
}
