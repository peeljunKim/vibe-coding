/* Native MySQL 테스트 연결 격리 검증 */
package com.newsverification.publisher.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 통합 테스트 연결값의 안전 경계 검증 */
class NativeMySqlTestConnectionGuardTest {

    /** Local 테스트 연결 허용 */
    @Test
    void acceptsExactLocalTestConnection() {
        NativeMySqlTestConnectionGuard.Settings settings =
                NativeMySqlTestConnectionGuard.validate(validEnvironment());

        assertThat(settings.databaseUrl()).isEqualTo(
                "jdbc:mysql://127.0.0.1:3306/fixture_app_test"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        assertThat(settings.username()).isEqualTo("fixture_app_test");
        assertThat(settings.password()).isEqualTo("fixture-password");
        assertThat(settings.toString()).doesNotContain("fixture-password");
    }

    /** 원격 Host 거절 */
    @Test
    void rejectsRemoteHost() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "TEST_DB_URL",
                "jdbc:mysql://db.example.test:3306/fixture_app_test"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");

        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 기대값과 다른 Database와 계정 거절 */
    @Test
    void rejectsUnexpectedDatabaseOrUsername() {
        Map<String, String> wrongDatabase = validEnvironment();
        wrongDatabase.put(
                "TEST_DB_URL",
                "jdbc:mysql://localhost:3306/other_test"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        Map<String, String> wrongUsername = validEnvironment();
        wrongUsername.put("TEST_DB_USERNAME", "other_test");

        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(wrongDatabase))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(wrongUsername))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 개발 연결과 Root 계정 거절 */
    @Test
    void rejectsDevelopmentOrRootSettings() {
        Map<String, String> developmentDatabase = validEnvironment();
        developmentDatabase.put("MYSQL_TEST_DATABASE", "fixture_app");
        developmentDatabase.put(
                "TEST_DB_URL",
                "jdbc:mysql://localhost:3306/fixture_app"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        Map<String, String> rootUsername = validEnvironment();
        rootUsername.put("MYSQL_TEST_USER", "root");
        rootUsername.put("TEST_DB_USERNAME", "root");

        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(developmentDatabase))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(rootUsername))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 위험 JDBC 속성과 비밀값 노출 거절 */
    @Test
    void rejectsUnsupportedJdbcPropertiesWithoutEchoingValues() {
        Map<String, String> environment = validEnvironment();
        environment.put(
                "TEST_DB_URL",
                "jdbc:mysql://localhost:3306/fixture_app_test"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                        + "&propertiesTransform=do-not-echo");

        assertThatThrownBy(() -> NativeMySqlTestConnectionGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("do-not-echo");
    }

    private Map<String, String> validEnvironment() {
        return new HashMap<>(Map.of(
                "TEST_DB_URL",
                "jdbc:mysql://127.0.0.1:3306/fixture_app_test"
                        + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC",
                "TEST_DB_USERNAME", "fixture_app_test",
                "TEST_DB_PASSWORD", "fixture-password",
                "MYSQL_TEST_DATABASE", "fixture_app_test",
                "MYSQL_TEST_USER", "fixture_app_test",
                "MYSQL_DATABASE", "fixture_app",
                "MYSQL_USER", "fixture_app"
        ));
    }
}
