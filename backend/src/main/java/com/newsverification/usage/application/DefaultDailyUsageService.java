/* 기존 분석 이용량 정책을 조합한 일일 조회 구현 */
package com.newsverification.usage.application;

import com.newsverification.headline.application.HeadlineAnalysisJobIdentityService;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisUsagePolicy;
import com.newsverification.headline.application.HeadlineAnalysisUsageResult;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import com.newsverification.health.application.HealthTopicFailureUsageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** 건강·제목 분석의 차감 없는 현재 이용량 조합 */
@Service
public class DefaultDailyUsageService implements DailyUsageService {

    private final HealthAnalysisJobIdentityService healthIdentityService;
    private final HeadlineAnalysisJobIdentityService headlineIdentityService;
    private final HealthTopicFailureUsagePolicy healthUsagePolicy;
    private final HeadlineAnalysisUsagePolicy headlineUsagePolicy;
    private final Clock clock;
    private final ZoneId usageZone;

    /** 기존 분석 식별·이용량 정책과 한국시간 구성 */
    public DefaultDailyUsageService(
            HealthAnalysisJobIdentityService healthIdentityService,
            HeadlineAnalysisJobIdentityService headlineIdentityService,
            HealthTopicFailureUsagePolicy healthUsagePolicy,
            HeadlineAnalysisUsagePolicy headlineUsagePolicy,
            Clock clock,
            @Value("${app.time-zone:Asia/Seoul}") ZoneId usageZone
    ) {
        this.healthIdentityService = Objects.requireNonNull(healthIdentityService);
        this.headlineIdentityService = Objects.requireNonNull(headlineIdentityService);
        this.healthUsagePolicy = Objects.requireNonNull(healthUsagePolicy);
        this.headlineUsagePolicy = Objects.requireNonNull(headlineUsagePolicy);
        this.clock = Objects.requireNonNull(clock);
        this.usageZone = Objects.requireNonNull(usageZone);
    }

    /** 동일 회원·비회원 식별자 기반 기능별 현재 이용량 조회 */
    @Override
    public Snapshot get(Requester requester) {
        Objects.requireNonNull(requester);
        HealthAnalysisJobIdentityService.PreparedIdentity healthIdentity = healthIdentityService.prepare(
                new HealthAnalysisJobService.Requester(
                        requester.memberId(), requester.guestBrowserId(), null, requester.clientIp()
                )
        );
        GuestBrowserCookie guestCookie = toGuestCookie(healthIdentity.guestBrowserCookie());
        String guestBrowserId = requester.guestBrowserId();
        if (guestCookie != null) {
            guestBrowserId = guestCookie.value();
        }
        HeadlineAnalysisJobIdentityService.PreparedIdentity headlineIdentity = headlineIdentityService.prepare(
                new HeadlineAnalysisJobService.Requester(
                        requester.memberId(), guestBrowserId, null, requester.clientIp()
                )
        );

        try {
            HealthTopicFailureUsageResult health = healthUsagePolicy.currentUsage(
                    healthIdentity.usageSubject()
            );
            HeadlineAnalysisUsageResult headline = headlineUsagePolicy.currentUsage(
                    headlineIdentity.usageSubject()
            );
            return new Snapshot(
                    usageZone.getId(),
                    nextResetAt(),
                    new Counter(
                            health.dailyLimit(),
                            health.usedCount(),
                            Math.max(0, health.dailyLimit() - health.usedCount())
                    ),
                    new Counter(
                            headline.dailyLimit(),
                            headline.usedCount(),
                            Math.max(0, headline.dailyLimit() - headline.usedCount())
                    ),
                    guestCookie
            );
        } catch (RuntimeException exception) {
            throw new DailyUsageServiceUnavailableException(exception);
        }
    }

    /** 다음 한국시간 자정 계산 */
    private Instant nextResetAt() {
        return clock.instant()
                .atZone(usageZone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(usageZone)
                .toInstant();
    }

    /** 건강 분석 Cookie 형식의 공통 조회 형식 변환 */
    private GuestBrowserCookie toGuestCookie(HealthAnalysisJobService.GuestBrowserCookie cookie) {
        if (cookie == null) {
            return null;
        }
        return new GuestBrowserCookie(cookie.value(), cookie.maxAge(), cookie.secure());
    }
}
