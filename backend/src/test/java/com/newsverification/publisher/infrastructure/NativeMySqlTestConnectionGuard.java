/* Native MySQL 테스트 연결 격리 Guard */
package com.newsverification.publisher.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/** Publisher 통합 테스트 전용 연결값 검증 */
final class NativeMySqlTestConnectionGuard {

    private static final Map<String, String> ALLOWED_QUERY_PARAMETERS = Map.of(
            "useUnicode", "true",
            "characterEncoding", "utf8",
            "serverTimezone", "UTC"
    );

    private NativeMySqlTestConnectionGuard() {
    }

    /** Process 환경 변수 기반 연결값 검증 */
    static Settings fromEnvironment() {
        return validate(System.getenv());
    }

    /** 연결값과 기대 격리값 일치 검증 */
    static Settings validate(Map<String, String> environment) {
        String databaseUrl = required(environment, "TEST_DB_URL");
        String username = required(environment, "TEST_DB_USERNAME");
        String password = required(environment, "TEST_DB_PASSWORD");
        String expectedDatabase = required(environment, "MYSQL_TEST_DATABASE");
        String expectedUsername = required(environment, "MYSQL_TEST_USER");
        String developmentDatabase = required(environment, "MYSQL_DATABASE");
        String developmentUsername = required(environment, "MYSQL_USER");

        validateTestName(expectedDatabase, "MYSQL_TEST_DATABASE");
        validateTestName(expectedUsername, "MYSQL_TEST_USER");
        if (expectedDatabase.equalsIgnoreCase(developmentDatabase)
                || expectedUsername.equalsIgnoreCase(developmentUsername)
                || expectedUsername.equalsIgnoreCase("root")
                || expectedUsername.equalsIgnoreCase("dev")) {
            throw new IllegalStateException("Native MySQL test settings must be isolated");
        }
        if (!username.equals(expectedUsername)) {
            throw new IllegalStateException("TEST_DB_USERNAME does not match MYSQL_TEST_USER");
        }

        URI uri = parseJdbcUri(databaseUrl);
        if (!"mysql".equals(uri.getScheme())
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || uri.getPort() != 3306
                || !("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))
                || !("/" + expectedDatabase).equals(uri.getRawPath())) {
            throw new IllegalStateException("TEST_DB_URL must use the exact local test database");
        }

        validateQuery(uri.getRawQuery());
        return new Settings(databaseUrl, username, password);
    }

    private static URI parseJdbcUri(String databaseUrl) {
        if (!databaseUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("TEST_DB_URL must be a MySQL JDBC URL");
        }
        try {
            return new URI(databaseUrl.substring("jdbc:".length()));
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("TEST_DB_URL is malformed");
        }
    }

    private static void validateQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalStateException("TEST_DB_URL query parameters are required");
        }

        Map<String, String> actualParameters = new HashMap<>();
        for (String parameter : rawQuery.split("&", -1)) {
            String[] parts = parameter.split("=", -1);
            if (parts.length != 2 || !ALLOWED_QUERY_PARAMETERS.containsKey(parts[0])) {
                throw new IllegalStateException("TEST_DB_URL contains an unsupported query parameter");
            }
            if (actualParameters.putIfAbsent(parts[0], parts[1]) != null
                    || !ALLOWED_QUERY_PARAMETERS.get(parts[0]).equals(parts[1])) {
                throw new IllegalStateException("TEST_DB_URL contains an invalid query parameter value");
            }
        }
        if (!actualParameters.equals(ALLOWED_QUERY_PARAMETERS)) {
            throw new IllegalStateException("TEST_DB_URL query parameters are incomplete");
        }
    }

    private static void validateTestName(String value, String name) {
        if (!value.matches("[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException(name + " must end with _test");
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the Native MySQL integration test");
        }
        return value;
    }

    record Settings(String databaseUrl, String username, String password) {

        @Override
        public String toString() {
            return "Settings[redacted]";
        }
    }
}
