/* 건강 분석 작업 소유권 생성과 검증 */
package com.newsverification.health.application;

import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** 회원 HMAC과 비회원 Cookie·작업 Token 결합 소유권 */
public class HealthAnalysisJobIdentityService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;

    private final Clock clock;
    private final ZoneId usageZone;
    private final byte[] keyMaterial;
    private final SecureRandom secureRandom;
    private final boolean secureCookie;
    private final HealthAnalysisUsageIdentityResolver usageIdentityResolver;

    /** 시각·비식별 Secret·난수 생성기 구성 */
    public HealthAnalysisJobIdentityService(
            Clock clock,
            ZoneId usageZone,
            byte[] keyMaterial,
            SecureRandom secureRandom,
            boolean secureCookie
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.usageZone = Objects.requireNonNull(usageZone);
        if (keyMaterial == null || keyMaterial.length == 0) {
            throw new IllegalArgumentException("Lookup HMAC key is required");
        }
        this.keyMaterial = keyMaterial.clone();
        this.secureRandom = Objects.requireNonNull(secureRandom);
        this.secureCookie = secureCookie;
        this.usageIdentityResolver = new HealthAnalysisUsageIdentityResolver(
                clock,
                usageZone,
                keyMaterial
        );
    }

    /** 접수용 소유권과 이용량 식별값 생성 */
    public PreparedIdentity prepare(HealthAnalysisJobService.Requester requester) {
        Objects.requireNonNull(requester);
        if (hasText(requester.memberId())) {
            HealthAnalysisUsageSubject usageSubject = usageIdentityResolver
                    .resolveMember(requester.memberId());
            return new PreparedIdentity(
                    new AnalysisJobOwner(
                            AnalysisJobOwnerType.MEMBER,
                            hmac("member-owner:" + requester.memberId().trim())
                    ),
                    usageSubject,
                    null,
                    null
            );
        }

        String browserId = hasText(requester.guestBrowserId())
                ? requester.guestBrowserId().trim()
                : randomToken();
        String accessToken = randomToken();
        HealthAnalysisUsageSubject usageSubject = usageIdentityResolver.resolveGuest(
                browserId,
                requester.clientIp()
        );
        HealthAnalysisJobService.GuestBrowserCookie browserCookie = hasText(requester.guestBrowserId())
                ? null
                : new HealthAnalysisJobService.GuestBrowserCookie(
                        browserId,
                        durationUntilNextReset(),
                        secureCookie
                );
        return new PreparedIdentity(
                guestOwner(browserId, accessToken),
                usageSubject,
                accessToken,
                browserCookie
        );
    }

    /** Polling 요청의 기대 소유권 복원 */
    public Optional<AnalysisJobOwner> resolve(HealthAnalysisJobService.Requester requester) {
        if (requester == null) {
            return Optional.empty();
        }
        if (hasText(requester.memberId())) {
            return Optional.of(new AnalysisJobOwner(
                    AnalysisJobOwnerType.MEMBER,
                    hmac("member-owner:" + requester.memberId().trim())
            ));
        }
        if (!hasText(requester.guestBrowserId()) || !hasText(requester.guestAccessToken())) {
            return Optional.empty();
        }
        return Optional.of(guestOwner(
                requester.guestBrowserId().trim(),
                requester.guestAccessToken().trim()
        ));
    }

    /** 비회원 Cookie와 작업 Token 결합 소유권 */
    private AnalysisJobOwner guestOwner(String browserId, String accessToken) {
        return new AnalysisJobOwner(
                AnalysisJobOwnerType.GUEST,
                hmac("guest-owner:" + browserId + ":" + accessToken)
        );
    }

    /** 한국시간 다음 자정까지 Cookie 수명 */
    private Duration durationUntilNextReset() {
        var now = clock.instant();
        var nextReset = now.atZone(usageZone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(usageZone)
                .toInstant();
        return Duration.between(now, nextReset);
    }

    /** 256-bit URL 안전 난수 */
    private String randomToken() {
        byte[] value = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** HMAC-SHA256 비식별 변환 */
    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyMaterial, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    /** 공백 제외 문자열 존재 확인 */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 접수 시 생성된 비식별 작업 자격 */
    public record PreparedIdentity(
            AnalysisJobOwner owner,
            HealthAnalysisUsageSubject usageSubject,
            String guestAccessToken,
            HealthAnalysisJobService.GuestBrowserCookie guestBrowserCookie
    ) {
    }
}
