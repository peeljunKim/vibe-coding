/* 기사 제목 분석 작업 접수와 Polling 검증 */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
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

    /** 고정 시각 기반 Service 구성 */
    private HeadlineAnalysisJobService service(InMemoryJobStore store, HeadlineAnalysisQueue queue) {
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
        private HeadlineAnalysisTask task;

        private RecordingQueue(boolean accepts) {
            this.accepts = accepts;
        }

        @Override
        public boolean enqueue(HeadlineAnalysisTask task) {
            this.task = task;
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
}
