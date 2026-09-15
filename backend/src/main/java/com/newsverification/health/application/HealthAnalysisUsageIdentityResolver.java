/* 건강 분석 이용량 비식별 Key 생성 */
package com.newsverification.health.application;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 회원 ID와 비회원 Cookie·IP의 HMAC 기반 식별 변환 */
public class HealthAnalysisUsageIdentityResolver {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Clock clock;
    private final ZoneId usageZone;
    private final byte[] identityKeyMaterial;

    /** 업무 시각과 외부 주입 HMAC Secret 구성 */
    public HealthAnalysisUsageIdentityResolver(
            Clock clock,
            ZoneId usageZone,
            byte[] identityKeyMaterial
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.usageZone = Objects.requireNonNull(usageZone);
        if (identityKeyMaterial == null || identityKeyMaterial.length == 0) {
            throw new IllegalArgumentException("Health usage identity secret is required");
        }
        this.identityKeyMaterial = identityKeyMaterial.clone();
    }

    /** 인증 회원 ID의 단일 비식별 Key 변환 */
    public HealthAnalysisUsageSubject resolveMember(String memberId) {
        String requiredMemberId = requireText(memberId, "Member id is required");
        return new HealthAnalysisUsageSubject(
                HealthAnalysisUserType.MEMBER,
                hmac("member:" + requiredMemberId)
        );
    }

    /** 비회원 Cookie와 날짜별 IP의 이중 비식별 Key 변환 */
    public HealthAnalysisUsageSubject resolveGuest(String browserCookieId, String clientIp) {
        String requiredCookieId = requireText(browserCookieId, "Guest browser cookie is required");
        String requiredClientIp = requireText(clientIp, "Guest client ip is required")
                .toLowerCase(Locale.ROOT);
        LocalDate usageDate = clock.instant().atZone(usageZone).toLocalDate();
        return new HealthAnalysisUsageSubject(
                HealthAnalysisUserType.GUEST,
                List.of(
                        hmac("guest-cookie:" + usageDate + ":" + requiredCookieId),
                        hmac("guest-ip:" + usageDate + ":" + requiredClientIp)
                )
        );
    }

    /** HMAC-SHA256 비식별 변환 */
    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(identityKeyMaterial, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    /** 필수 외부 식별 문자열 검증 */
    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
