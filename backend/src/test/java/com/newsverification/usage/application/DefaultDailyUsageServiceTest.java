/* 기능별 일일 이용량 Application Service 검증 */
package com.newsverification.usage.application;

import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.headline.application.HeadlineAnalysisCacheUsageResult;
import com.newsverification.headline.application.HeadlineAnalysisJobIdentityService;
import com.newsverification.headline.application.HeadlineAnalysisUsagePolicy;
import com.newsverification.headline.application.HeadlineAnalysisUsageResult;
import com.newsverification.headline.application.HeadlineAnalysisUsageSubject;
import com.newsverification.health.application.HealthAnalysisJobIdentityService;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisUsageSubject;
import com.newsverification.health.application.HealthTopicFailureUsagePolicy;
import com.newsverification.health.application.HealthTopicFailureUsageResult;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 기존 분석 식별자와 차감 없는 이용량 조회 조합 검증 */
class DefaultDailyUsageServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-04T14:30:00Z");
    private static final byte[] HMAC_KEY = "test-lookup-hmac-key".getBytes();

    /** 회원 기능별 현재 이용량과 다음 한국시간 자정 계산 */
    @Test
    void getsMemberUsageWithoutChangingCounters() {
        var healthPolicy = new StubHealthUsagePolicy(2);
        var headlinePolicy = new StubHeadlineUsagePolicy(4);
        var service = service(healthPolicy, headlinePolicy);

        DailyUsageService.Snapshot snapshot = service.get(
                new DailyUsageService.Requester("member-1", null, "203.0.113.10")
        );

        assertThat(snapshot.timezone()).isEqualTo("Asia/Seoul");
        assertThat(snapshot.resetsAt()).isEqualTo(Instant.parse("2026-10-04T15:00:00Z"));
        assertThat(snapshot.health()).isEqualTo(new DailyUsageService.Counter(5, 2, 3));
        assertThat(snapshot.headline()).isEqualTo(new DailyUsageService.Counter(10, 4, 6));
        assertThat(snapshot.guestBrowserCookie()).isNull();
        assertThat(healthPolicy.subject.identifierKeys())
                .containsExactlyElementsOf(headlinePolicy.subject.identifierKeys());
    }

    /** 첫 비회원 조회의 공통 브라우저 식별자와 자정 수명 Cookie 발급 */
    @Test
    void issuesOneGuestIdentityForBothFeatures() {
        var healthPolicy = new StubHealthUsagePolicy(1);
        var headlinePolicy = new StubHeadlineUsagePolicy(2);
        var service = service(healthPolicy, headlinePolicy);

        DailyUsageService.Snapshot snapshot = service.get(
                new DailyUsageService.Requester(null, null, "203.0.113.10")
        );

        assertThat(snapshot.health()).isEqualTo(new DailyUsageService.Counter(2, 1, 1));
        assertThat(snapshot.headline()).isEqualTo(new DailyUsageService.Counter(5, 2, 3));
        assertThat(snapshot.guestBrowserCookie()).isNotNull();
        assertThat(snapshot.guestBrowserCookie().value()).isNotBlank();
        assertThat(snapshot.guestBrowserCookie().maxAge()).hasMinutes(30);
        assertThat(healthPolicy.subject.identifierKeys())
                .containsExactlyElementsOf(headlinePolicy.subject.identifierKeys());
    }

    /** Redis 조회 오류의 공개 서비스 장애 변환 */
    @Test
    void convertsUsageStoreFailureToServiceUnavailable() {
        var healthPolicy = new StubHealthUsagePolicy(0);
        healthPolicy.failure = new IllegalStateException("redis unavailable");

        assertThatThrownBy(() -> service(healthPolicy, new StubHeadlineUsagePolicy(0)).get(
                new DailyUsageService.Requester("member-1", null, "203.0.113.10")
        )).isInstanceOf(DailyUsageServiceUnavailableException.class)
                .hasCause(healthPolicy.failure);
    }

    /** 식별자 준비 가용성 오류의 공개 서비스 장애 변환 */
    @Test
    void convertsIdentityPreparationFailureToServiceUnavailable() {
        var failure = new IllegalStateException("HMAC-SHA256 unavailable");
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        var failingIdentityService = new HealthAnalysisJobIdentityService(
                clock, KOREA_ZONE, HMAC_KEY, new SecureRandom(), false
        ) {
            @Override
            public PreparedIdentity prepare(HealthAnalysisJobService.Requester requester) {
                throw failure;
            }
        };

        assertThatThrownBy(() -> service(
                failingIdentityService,
                new StubHealthUsagePolicy(0),
                new StubHeadlineUsagePolicy(0)
        ).get(new DailyUsageService.Requester("member-1", null, "203.0.113.10")))
                .isInstanceOf(DailyUsageServiceUnavailableException.class)
                .hasCause(failure);
    }

    /** 고정 시각과 기존 분석 Identity Service 구성 */
    private DefaultDailyUsageService service(
            StubHealthUsagePolicy healthPolicy,
            StubHeadlineUsagePolicy headlinePolicy
    ) {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        return service(
                new HealthAnalysisJobIdentityService(
                        clock, KOREA_ZONE, HMAC_KEY, new SecureRandom(), false
                ),
                healthPolicy,
                headlinePolicy
        );
    }

    /** 주입된 건강 식별 Service와 기존 제목 식별 Service 구성 */
    private DefaultDailyUsageService service(
            HealthAnalysisJobIdentityService healthIdentityService,
            StubHealthUsagePolicy healthPolicy,
            StubHeadlineUsagePolicy headlinePolicy
    ) {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        return new DefaultDailyUsageService(
                healthIdentityService,
                new HeadlineAnalysisJobIdentityService(
                        clock, KOREA_ZONE, HMAC_KEY, new SecureRandom(), false
                ),
                healthPolicy,
                headlinePolicy,
                clock,
                KOREA_ZONE
        );
    }

    /** 건강 이용량 조회 전용 Test Double */
    private static final class StubHealthUsagePolicy implements HealthTopicFailureUsagePolicy {

        private final int usedCount;
        private HealthAnalysisUsageSubject subject;
        private RuntimeException failure;

        private StubHealthUsagePolicy(int usedCount) {
            this.usedCount = usedCount;
        }

        @Override
        public HealthTopicFailureUsageResult currentUsage(HealthAnalysisUsageSubject subject) {
            this.subject = subject;
            if (failure != null) {
                throw failure;
            }
            return new HealthTopicFailureUsageResult(false, usedCount, subject.userType().dailyLimit());
        }

        @Override
        public void verifyCanStart(HealthAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HealthTopicFailureUsageResult recordFailure(HealthAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HealthTopicFailureUsageResult recordAnalysisStart(HealthAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HealthTopicFailureUsageResult> recordCacheAccess(
                HealthAnalysisUsageSubject subject,
                AnalysisCacheKey cacheKey,
                String viewerFingerprint
        ) {
            throw new UnsupportedOperationException();
        }
    }

    /** 제목 이용량 조회 전용 Test Double */
    private static final class StubHeadlineUsagePolicy implements HeadlineAnalysisUsagePolicy {

        private final int usedCount;
        private HeadlineAnalysisUsageSubject subject;

        private StubHeadlineUsagePolicy(int usedCount) {
            this.usedCount = usedCount;
        }

        @Override
        public HeadlineAnalysisUsageResult currentUsage(HeadlineAnalysisUsageSubject subject) {
            this.subject = subject;
            return new HeadlineAnalysisUsageResult(usedCount, subject.userType().dailyLimit());
        }

        @Override
        public void verifyCanStart(HeadlineAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HeadlineAnalysisUsageResult recordAnalysisStart(HeadlineAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HeadlineAnalysisCacheUsageResult> recordCacheAccess(
                HeadlineAnalysisUsageSubject subject,
                AnalysisCacheKey cacheKey,
                String viewerFingerprint
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
