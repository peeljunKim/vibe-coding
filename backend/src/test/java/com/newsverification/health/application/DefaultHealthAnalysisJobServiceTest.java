/* 건강 분석 작업 접수와 소유권 검증 */
package com.newsverification.health.application;

import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.domain.AnalysisJob;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 비회원 Token·Cookie와 회원 소유권 유지 */
class DefaultHealthAnalysisJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");

    /** 비회원 Cookie와 작업 Token 모두 일치할 때만 조회 */
    @Test
    void acceptsAndFindsGuestOwnedJob() {
        var store = new InMemoryJobStore();
        var queue = new RecordingQueue(true);
        HealthAnalysisJobService service = service(store, queue);

        HealthAnalysisJobService.Acceptance accepted = service.accept(
                "https://news.example/article",
                new HealthAnalysisJobService.Requester(null, null, null, "203.0.113.7")
        );

        assertThat(accepted.guestBrowserCookie()).isNotNull();
        assertThat(accepted.guestAccessToken()).isNotBlank();
        assertThat(queue.task).isNotNull();
        assertThat(queue.task.usageIdentifierKeys()).hasSize(2);
        assertThat(service.find(
                accepted.analysisId(),
                new HealthAnalysisJobService.Requester(
                        null,
                        accepted.guestBrowserCookie().value(),
                        accepted.guestAccessToken(),
                        "203.0.113.7"
                )
        )).isPresent();
        assertThat(service.find(
                accepted.analysisId(),
                new HealthAnalysisJobService.Requester(
                        null,
                        accepted.guestBrowserCookie().value(),
                        "wrong-token",
                        "203.0.113.7"
                )
        )).isEmpty();
    }

    /** 인증 회원 ID의 비식별 소유권 조회 */
    @Test
    void acceptsAndFindsMemberOwnedJob() {
        var store = new InMemoryJobStore();
        HealthAnalysisJobService service = service(store, new RecordingQueue(true));
        var requester = new HealthAnalysisJobService.Requester(
                "member-1",
                null,
                null,
                "203.0.113.7"
        );

        HealthAnalysisJobService.Acceptance accepted = service.accept(
                "https://news.example/member-article",
                requester
        );

        assertThat(accepted.guestAccessToken()).isNull();
        assertThat(accepted.guestBrowserCookie()).isNull();
        assertThat(service.find(accepted.analysisId(), requester)).isPresent();
        assertThat(service.find(
                accepted.analysisId(),
                new HealthAnalysisJobService.Requester("member-2", null, null, "203.0.113.7")
        )).isEmpty();
    }

    /** Queue 포화의 접수 성공 위장 차단 */
    @Test
    void rejectsAcceptanceWhenQueueIsFull() {
        var store = new InMemoryJobStore();
        HealthAnalysisJobService service = service(store, new RecordingQueue(false));

        assertThatThrownBy(() -> service.accept(
                "https://news.example/article",
                new HealthAnalysisJobService.Requester("member-1", null, null, "203.0.113.7")
        )).isInstanceOf(HealthAnalysisServiceUnavailableException.class);

        assertThat(store.jobs.values())
                .allMatch(job -> job.status().name().equals("FAILED"));
    }

    /** 고정 시각 기반 Service 구성 */
    private HealthAnalysisJobService service(InMemoryJobStore store, HealthAnalysisQueue queue) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var identityService = new HealthAnalysisJobIdentityService(
                clock,
                ZoneId.of("Asia/Seoul"),
                "test-lookup-hmac-key".getBytes(),
                new SecureRandom(),
                false
        );
        return new DefaultHealthAnalysisJobService(
                new AnalysisJobLifecycleService(store, clock),
                store,
                queue,
                identityService,
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
    private static final class RecordingQueue implements HealthAnalysisQueue {

        private final boolean accepts;
        private HealthAnalysisTask task;

        private RecordingQueue(boolean accepts) {
            this.accepts = accepts;
        }

        @Override
        public boolean enqueue(HealthAnalysisTask task) {
            this.task = task;
            return accepts;
        }

        @Override
        public Optional<HealthAnalysisTask> take() {
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
}
