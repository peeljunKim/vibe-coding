/* 기사 제목 분석 작업 접수와 Polling 검증 */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisRequestRateLimitExceededException;
import com.newsverification.analysis.application.AnalysisRequestRateLimiter;
import com.newsverification.analysis.domain.AnalysisJob;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 제목 전용 한도와 Queue 입력 및 소유권 조회 검증 */
class DefaultHeadlineAnalysisJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    /** 비회원 제목 분석의 독립 한도와 Queue 접수 */
    @Test
    void acceptsGuestJobWithHeadlineLimitAndSeparateTask() {
        InMemoryJobStore store = new InMemoryJobStore();
        RecordingQueue queue = new RecordingQueue(true);
        HeadlineAnalysisJobService service = service(store, queue);

        HeadlineAnalysisJobService.Acceptance acceptance = service.accept(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester(null, null, null, "203.0.113.10")
        );

        assertThat(acceptance.usage()).isEqualTo(new HeadlineAnalysisJobService.Usage(5, 0, 5, false));
        assertThat(acceptance.guestAccessToken()).isNotBlank();
        assertThat(acceptance.guestBrowserCookie()).isNotNull();
        assertThat(queue.task.articleUrl()).isEqualTo("https://news.example/general");
        assertThat(queue.task.userType()).isEqualTo(HeadlineAnalysisUserType.GUEST);
    }

    /** 사용자 동의 재분석의 Queue 표시 전달 */
    @Test
    void marksConfirmedReanalysisTask() {
        InMemoryJobStore store = new InMemoryJobStore();
        RecordingQueue queue = new RecordingQueue(true);
        HeadlineAnalysisJobService service = service(store, queue);

        service.reanalyze(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester("member-1", null, null, "203.0.113.10")
        );

        assertThat(queue.task.reanalysisRequested()).isTrue();
    }

    /** 다른 비회원 자격의 작업 조회 차단 */
    @Test
    void hidesJobFromDifferentGuestAccessToken() {
        InMemoryJobStore store = new InMemoryJobStore();
        HeadlineAnalysisJobService service = service(store, new RecordingQueue(true));
        HeadlineAnalysisJobService.Acceptance acceptance = service.accept(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester(null, null, null, "203.0.113.10")
        );

        Optional<HeadlineAnalysisJobService.Progress> progress = service.find(
                acceptance.analysisId(),
                new HeadlineAnalysisJobService.Requester(
                        null,
                        acceptance.guestBrowserCookie().value(),
                        "wrong-token",
                        "203.0.113.10"
                )
        );

        assertThat(progress).isEmpty();
    }

    /** 로그인 이후에도 기존 비회원 제목 작업 조회 */
    @Test
    void findsGuestOwnedJobAfterLogin() {
        InMemoryJobStore store = new InMemoryJobStore();
        HeadlineAnalysisJobService service = service(store, new RecordingQueue(true));
        HeadlineAnalysisJobService.Acceptance acceptance = service.accept(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester(null, null, null, "203.0.113.10")
        );

        assertThat(service.find(
                acceptance.analysisId(),
                new HeadlineAnalysisJobService.Requester(
                        "member-1",
                        acceptance.guestBrowserCookie().value(),
                        acceptance.guestAccessToken(),
                        "203.0.113.10"
                )
        )).isPresent();
    }

    /** Queue 예외 이후 생성된 제목 작업 정리 */
    @Test
    void failsAcceptedJobWhenQueueThrows() {
        InMemoryJobStore store = new InMemoryJobStore();
        HeadlineAnalysisJobService service = service(
                store,
                new RecordingQueue(new IllegalStateException("Redis unavailable"))
        );

        assertThatThrownBy(() -> service.accept(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester("member-1", null, null, "203.0.113.10")
        )).isInstanceOf(HeadlineAnalysisServiceUnavailableException.class);

        assertThat(store.jobs.values()).allMatch(job -> job.status().name().equals("FAILED"));
    }

    /** 동일 사용자의 과도한 제목 분석 접수를 Queue 전에 차단 */
    @Test
    void rejectsSixthRequestBeforeCreatingJobOrUsingQueue() {
        InMemoryJobStore store = new InMemoryJobStore();
        RecordingQueue queue = new RecordingQueue(true);
        var rateLimiter = new RecordingRequestRateLimiter(5);
        HeadlineAnalysisJobService service = service(store, queue, rateLimiter);
        var requester = new HeadlineAnalysisJobService.Requester(
                "member-1",
                null,
                null,
                "203.0.113.10"
        );

        for (int index = 0; index < 5; index++) {
            service.accept("https://news.example/general", requester);
        }

        assertThatThrownBy(() -> service.accept("https://news.example/general", requester))
                .isInstanceOf(AnalysisRequestRateLimitExceededException.class);
        assertThat(store.jobs).hasSize(5);
        assertThat(queue.enqueueCount).isEqualTo(5);
    }

    /** 요청 제한 Redis 장애의 제목 작업 생성 전 서비스 장애 변환 */
    @Test
    void rejectsAcceptanceBeforeCreatingJobWhenRateLimiterFails() {
        InMemoryJobStore store = new InMemoryJobStore();
        RecordingQueue queue = new RecordingQueue(true);
        HeadlineAnalysisJobService service = service(store, queue, (feature, identifiers) -> {
            throw new IllegalStateException("Redis unavailable");
        });

        assertThatThrownBy(() -> service.accept(
                "https://news.example/general",
                new HeadlineAnalysisJobService.Requester("member-1", null, null, "203.0.113.10")
        )).isInstanceOf(HeadlineAnalysisServiceUnavailableException.class);
        assertThat(store.jobs).isEmpty();
        assertThat(queue.enqueueCount).isZero();
    }

    /** 제목 상태 Polling의 분석 접수 제한 제외 */
    @Test
    void doesNotRateLimitPollingRequests() {
        InMemoryJobStore store = new InMemoryJobStore();
        var rateLimiter = new RecordingRequestRateLimiter(1);
        HeadlineAnalysisJobService service = service(store, new RecordingQueue(true), rateLimiter);
        var requester = new HeadlineAnalysisJobService.Requester(
                "member-1", null, null, "203.0.113.10"
        );
        HeadlineAnalysisJobService.Acceptance accepted = service.accept(
                "https://news.example/general", requester);

        assertThat(service.find(accepted.analysisId(), requester)).isPresent();
        assertThat(service.find(accepted.analysisId(), requester)).isPresent();
        assertThat(rateLimiter.count).isEqualTo(1);
    }

    /** 고정 시각 기반 Service 구성 */
    private HeadlineAnalysisJobService service(InMemoryJobStore store, HeadlineAnalysisQueue queue) {
        return service(store, queue, AnalysisRequestRateLimiter.unlimited());
    }

    /** 요청 제한 정책 포함 제목 Service 구성 */
    private HeadlineAnalysisJobService service(
            InMemoryJobStore store,
            HeadlineAnalysisQueue queue,
            AnalysisRequestRateLimiter requestRateLimiter
    ) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DefaultHeadlineAnalysisJobService(
                new AnalysisJobLifecycleService(store, clock),
                store,
                queue,
                new HeadlineAnalysisJobIdentityService(
                        clock,
                        ZoneId.of("Asia/Seoul"),
                        "test-lookup-hmac-key".getBytes(),
                        new SecureRandom(),
                        false
                ),
                new StubUsagePolicy(),
                requestRateLimiter,
                new ObjectMapper()
        );
    }

    /** 테스트용 작업과 결과 저장소 */
    private static final class InMemoryJobStore implements AnalysisJobStore, AnalysisJobOutcomeStore {

        private final Map<String, AnalysisJob> jobs = new HashMap<>();
        private final Map<String, AnalysisJobOutcome> outcomes = new HashMap<>();

        @Override
        public boolean create(AnalysisJob job) {
            return jobs.putIfAbsent(job.id(), job) == null;
        }

        @Override
        public Optional<AnalysisJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public boolean replace(String jobId, long expectedVersion, AnalysisJob updatedJob) {
            AnalysisJob current = jobs.get(jobId);
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            jobs.put(jobId, updatedJob);
            return true;
        }

        @Override
        public boolean replaceWithOutcome(
                String jobId,
                long expectedVersion,
                AnalysisJob updatedJob,
                AnalysisJobOutcome outcome
        ) {
            if (!replace(jobId, expectedVersion, updatedJob)) {
                return false;
            }
            outcomes.put(jobId, outcome);
            return true;
        }

        @Override
        public Optional<AnalysisJobOutcome> findOutcome(String jobId) {
            return Optional.ofNullable(outcomes.get(jobId));
        }
    }

    /** 접수 결과 기록 Queue */
    private static final class RecordingQueue implements HeadlineAnalysisQueue {

        private final boolean accepts;
        private final RuntimeException failure;
        private HeadlineAnalysisTask task;
        private int enqueueCount;

        private RecordingQueue(boolean accepts) {
            this.accepts = accepts;
            this.failure = null;
        }

        private RecordingQueue(RuntimeException failure) {
            this.accepts = false;
            this.failure = failure;
        }

        @Override
        public boolean enqueue(HeadlineAnalysisTask task) {
            if (failure != null) {
                throw failure;
            }
            this.task = task;
            enqueueCount++;
            return accepts;
        }

        @Override
        public Optional<HeadlineAnalysisTask> take() {
            return Optional.empty();
        }

        @Override
        public boolean tryAcquireWorker(String ownerToken, Duration leaseTime) {
            return true;
        }

        @Override
        public void releaseWorker(String ownerToken) {
        }
    }

    /** 테스트용 메모리 요청 제한 정책 */
    private static final class RecordingRequestRateLimiter implements AnalysisRequestRateLimiter {

        private final int limit;
        private int count;

        private RecordingRequestRateLimiter(int limit) {
            this.limit = limit;
        }

        @Override
        public void acquire(Feature feature, java.util.List<String> identifierKeys) {
            if (count >= limit) {
                throw new AnalysisRequestRateLimitExceededException();
            }
            count++;
        }
    }

    /** 테스트용 제목 이용량 정책 */
    private static final class StubUsagePolicy implements HeadlineAnalysisUsagePolicy {

        @Override
        public HeadlineAnalysisUsageResult currentUsage(HeadlineAnalysisUsageSubject subject) {
            return new HeadlineAnalysisUsageResult(0, subject.userType().dailyLimit());
        }

        @Override
        public void verifyCanStart(HeadlineAnalysisUsageSubject subject) {
        }

        @Override
        public HeadlineAnalysisUsageResult recordAnalysisStart(HeadlineAnalysisUsageSubject subject) {
            throw new UnsupportedOperationException();
        }
    }
}
