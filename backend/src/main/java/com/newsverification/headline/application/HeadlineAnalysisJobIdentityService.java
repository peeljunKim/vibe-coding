/* 기사 제목 분석 작업 소유권 생성과 검증 */
package com.newsverification.headline.application;

import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** 회원 HMAC과 비회원 Cookie·작업 Token 결합 소유권 */
public class HeadlineAnalysisJobIdentityService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_BYTES = 32;

    private final Clock clock;
    private final ZoneId usageZone;
    private final byte[] keyMaterial;
    private final SecureRandom secureRandom;
    private final boolean secureCookie;

    /** 시각·비식별 Secret·난수 생성기 구성 */
    public HeadlineAnalysisJobIdentityService(
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
    }

    /** 접수용 소유권과 제목 이용량 식별값 생성 */
    public PreparedIdentity prepare(HeadlineAnalysisJobService.Requester requester) {
        Objects.requireNonNull(requester);
        if (hasText(requester.memberId())) {
            String memberId = requester.memberId().trim();
            return new PreparedIdentity(
                    new AnalysisJobOwner(AnalysisJobOwnerType.MEMBER, hmac("member-owner:" + memberId)),
                    new HeadlineAnalysisUsageSubject(
                            HeadlineAnalysisUserType.MEMBER,
                            List.of(hmac("member:" + memberId))
                    ),
                    null,
                    null
            );
        }

        String browserId = hasText(requester.guestBrowserId())
                ? requester.guestBrowserId().trim()
                : randomToken();
        String clientIp = requireText(requester.clientIp(), "Guest client ip is required")
                .toLowerCase(Locale.ROOT);
        String accessToken = randomToken();
        LocalDate usageDate = clock.instant().atZone(usageZone).toLocalDate();
        HeadlineAnalysisJobService.GuestBrowserCookie browserCookie = hasText(requester.guestBrowserId())
                ? null
                : new HeadlineAnalysisJobService.GuestBrowserCookie(
                        browserId,
                        durationUntilNextReset(),
                        secureCookie
                );
        return new PreparedIdentity(
                guestOwner(browserId, accessToken),
                new HeadlineAnalysisUsageSubject(
                        HeadlineAnalysisUserType.GUEST,
                        List.of(
                                hmac("guest-cookie:" + usageDate + ":" + browserId),
                                hmac("guest-ip:" + usageDate + ":" + clientIp)
                        )
                ),
                accessToken,
                browserCookie
        );
    }

    /** Polling 요청의 기대 소유권 복원 */
    public Optional<AnalysisJobOwner> resolve(HeadlineAnalysisJobService.Requester requester) {
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
        var nextReset = now.atZone(usageZone).toLocalDate().plusDays(1)
                .atStartOfDay(usageZone).toInstant();
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

    /** 필수 문자열 확인 */
    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /** 공백 제외 문자열 존재 확인 */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 접수 시 생성된 제목 작업 자격 */
    public record PreparedIdentity(
            AnalysisJobOwner owner,
            HeadlineAnalysisUsageSubject usageSubject,
            String guestAccessToken,
            HeadlineAnalysisJobService.GuestBrowserCookie guestBrowserCookie
    ) {
    }
}
