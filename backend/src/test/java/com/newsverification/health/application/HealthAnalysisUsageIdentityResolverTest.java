/* 건강 분석 이용량 식별 Key 생성 검증 */
package com.newsverification.health.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 회원 ID와 비회원 이중 신호의 비식별 Key 검증 */
class HealthAnalysisUsageIdentityResolverTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final byte[] TEST_SECRET = "test-only-identity-secret"
            .getBytes(StandardCharsets.UTF_8);

    /** 동일 회원의 안정적인 단일 Key */
    @Test
    void resolvesStableMemberIdentity() {
        HealthAnalysisUsageIdentityResolver resolver = resolver("2026-09-15T01:00:00Z");

        HealthAnalysisUsageSubject first = resolver.resolveMember("member-42");
        HealthAnalysisUsageSubject second = resolver.resolveMember("member-42");

        assertThat(first).isEqualTo(second);
        assertThat(first.userType()).isEqualTo(HealthAnalysisUserType.MEMBER);
        assertThat(first.identifierKeys()).hasSize(1);
        assertThat(first.identifierKeys().get(0)).doesNotContain("member-42");
    }

    /** 동일 비회원 Cookie와 IP의 안정적인 이중 Key */
    @Test
    void resolvesStableGuestCookieAndIpIdentities() {
        HealthAnalysisUsageIdentityResolver resolver = resolver("2026-09-15T01:00:00Z");

        HealthAnalysisUsageSubject first = resolver.resolveGuest("browser-cookie", "203.0.113.10");
        HealthAnalysisUsageSubject second = resolver.resolveGuest("browser-cookie", "203.0.113.10");

        assertThat(first).isEqualTo(second);
        assertThat(first.userType()).isEqualTo(HealthAnalysisUserType.GUEST);
        assertThat(first.identifierKeys()).hasSize(2);
        assertThat(first.identifierKeys())
                .allSatisfy(key -> assertThat(key)
                        .doesNotContain("browser-cookie")
                        .doesNotContain("203.0.113.10"));
    }

    /** Cookie 변경 시 유지되는 IP 식별 Key */
    @Test
    void preservesIpIdentityWhenGuestCookieChanges() {
        HealthAnalysisUsageIdentityResolver resolver = resolver("2026-09-15T01:00:00Z");

        HealthAnalysisUsageSubject original = resolver.resolveGuest("cookie-a", "203.0.113.10");
        HealthAnalysisUsageSubject changed = resolver.resolveGuest("cookie-b", "203.0.113.10");

        assertThat(original.identifierKeys()).containsAnyElementsOf(changed.identifierKeys());
        assertThat(original.identifierKeys()).isNotEqualTo(changed.identifierKeys());
    }

    /** IP 변경 시 유지되는 Cookie 식별 Key */
    @Test
    void preservesCookieIdentityWhenGuestIpChanges() {
        HealthAnalysisUsageIdentityResolver resolver = resolver("2026-09-15T01:00:00Z");

        HealthAnalysisUsageSubject original = resolver.resolveGuest("cookie-a", "203.0.113.10");
        HealthAnalysisUsageSubject changed = resolver.resolveGuest("cookie-a", "203.0.113.11");

        assertThat(original.identifierKeys()).containsAnyElementsOf(changed.identifierKeys());
        assertThat(original.identifierKeys()).isNotEqualTo(changed.identifierKeys());
    }

    /** 한국시간 날짜 변경의 비회원 Key 초기화 */
    @Test
    void changesGuestIdentityAtKoreaMidnight() {
        HealthAnalysisUsageIdentityResolver beforeMidnight = resolver("2026-09-15T14:59:59Z");
        HealthAnalysisUsageIdentityResolver afterMidnight = resolver("2026-09-15T15:00:00Z");

        HealthAnalysisUsageSubject before = beforeMidnight.resolveGuest("cookie-a", "203.0.113.10");
        HealthAnalysisUsageSubject after = afterMidnight.resolveGuest("cookie-a", "203.0.113.10");

        assertThat(before.identifierKeys()).doesNotContainAnyElementsOf(after.identifierKeys());
    }

    /** 빈 외부 식별 입력 거절 */
    @Test
    void rejectsBlankIdentityInputs() {
        HealthAnalysisUsageIdentityResolver resolver = resolver("2026-09-15T01:00:00Z");

        assertThatThrownBy(() -> resolver.resolveMember(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolveGuest(" ", "203.0.113.10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolveGuest("cookie-a", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 고정 시각과 테스트 전용 HMAC Secret 구성 */
    private HealthAnalysisUsageIdentityResolver resolver(String instant) {
        return new HealthAnalysisUsageIdentityResolver(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                KOREA_ZONE,
                TEST_SECRET
        );
    }
}
