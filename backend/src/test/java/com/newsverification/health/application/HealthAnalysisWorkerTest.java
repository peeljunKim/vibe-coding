/* 건강 분석 Background Worker 검증 */
package com.newsverification.health.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 단일 소비·단계 전환·완료·실패·Deadline 검증 */
class HealthAnalysisWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");
    private static final AnalysisJobOwner OWNER = new AnalysisJobOwner(
            AnalysisJobOwnerType.MEMBER,
            "member-owner-key"
    );

    /** 안전 수집과 분야 판별 이후 Mock 결과 완료 */
    @Test
    void completesQueuedHealthAnalysisOnce() {
        var store = new InMemoryJobStore();
        AnalysisJobLifecycleService lifecycle = lifecycle(store, NOW);
        AnalysisJob accepted = lifecycle.accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        var queue = new SingleTaskQueue(task, true);
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        ExtractedArticle article = article();
        HealthArticleScreeningResult screening = new HealthArticleScreeningResult(
                article,
                HealthArticleTopicDecision.HEALTH_RELATED
        );
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenReturn(screening);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(true, 1, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenReturn(new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(result(article))
                ));
        HealthAnalysisWorker worker = worker(store, queue, useCase, usagePolicy, NOW);

        assertThat(worker.runOnce()).isTrue();

        AnalysisJob completed = store.findById(accepted.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().type())
                .isEqualTo(AnalysisJobOutcome.Type.RESULT);
        assertThat(queue.deliveryCount).isEqualTo(1);
        assertThat(worker.runOnce()).isFalse();
        verify(useCase).screen(task.articleUrl(), task.usageSubject());
        verify(usagePolicy).recordAnalysisStart(task.usageSubject());
    }

    /** 기사 수집 실패의 종료 상태와 무재시도 */
    @Test
    void failsDequeuedTaskWithoutRetry() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        var queue = new SingleTaskQueue(task, true);
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        when(useCase.screen(task.articleUrl(), task.usageSubject()))
                .thenThrow(new ArticleProcessingException(ArticleProcessingError.DOWNLOAD_FAILED));
        HealthAnalysisWorker worker = worker(
                store,
                queue,
                useCase,
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isTrue();
        assertThat(worker.runOnce()).isFalse();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        assertThat(outcome.errorCode()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(queue.deliveryCount).isEqualTo(1);
    }

    /** Queue 대기를 포함한 90초 Deadline 이후 실행 차단 */
    @Test
    void failsExpiredQueuedTaskBeforeArticleRead() {
        var store = new InMemoryJobStore();
        Instant acceptedAt = NOW.minusSeconds(90);
        AnalysisJob accepted = lifecycle(store, acceptedAt).accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthAnalysisWorker worker = worker(
                store,
                new SingleTaskQueue(task, true),
                useCase,
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().errorCode())
                .isEqualTo("ANALYSIS_DEADLINE_EXCEEDED");
        verify(useCase, never()).screen(task.articleUrl(), task.usageSubject());
    }

    /** 기사 수집 중 Deadline 경과 시 분석 시작과 차감 차단 */
    @Test
    void stopsBeforeAnalysisWhenDeadlinePassesDuringScreening() {
        var store = new InMemoryJobStore();
        var clock = new MutableClock(NOW);
        AnalysisJobLifecycleService lifecycle = new AnalysisJobLifecycleService(store, clock);
        AnalysisJob accepted = lifecycle.accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(90));
            return new HealthArticleScreeningResult(
                    article(),
                    HealthArticleTopicDecision.HEALTH_RELATED
            );
        });
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                lifecycle,
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().errorCode())
                .isEqualTo("ANALYSIS_DEADLINE_EXCEEDED");
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
        verify(useCase, never()).continueAfterScreening(
                screeningResult(),
                task.usageSubject(),
                accepted.deadlineAt()
        );
    }

    /** Worker Lease 획득 실패 시 Queue 미소비 */
    @Test
    void doesNotConsumeWhenAnotherWorkerOwnsLease() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-1", OWNER);
        var queue = new SingleTaskQueue(task(accepted.id()), false);
        HealthAnalysisWorker worker = worker(
                store,
                queue,
                mock(HealthAnalysisUseCase.class),
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isFalse();
        assertThat(queue.takeCount).isZero();
        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.PROCESSING);
    }

    /** 고정 의존성 Worker 구성 */
    private HealthAnalysisWorker worker(
            InMemoryJobStore store,
            HealthAnalysisQueue queue,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            Instant now
    ) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new HealthAnalysisWorker(
                queue,
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                clock,
                "test-worker"
        );
    }

    /** 고정 시각 작업 수명 Service */
    private AnalysisJobLifecycleService lifecycle(InMemoryJobStore store, Instant now) {
        return new AnalysisJobLifecycleService(store, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Queue 작업 Fixture */
    private HealthAnalysisTask task(String analysisId) {
        return new HealthAnalysisTask(
                analysisId,
                "https://news.example/article",
                HealthAnalysisUserType.MEMBER,
                List.of("member-usage-key")
        );
    }

    /** 추출 기사 Fixture */
    private ExtractedArticle article() {
        return new ExtractedArticle(
                URI.create("https://news.example/article"),
                "독감 예방접종 대상 안내",
                "질병관리청은 독감 예방접종 대상과 시기를 안내했습니다.",
                OffsetDateTime.parse("2026-09-18T09:00:00+09:00"),
                Optional.empty()
        );
    }

    /** 건강 기사 분야 판별 Fixture */
    private HealthArticleScreeningResult screeningResult() {
        return new HealthArticleScreeningResult(
                article(),
                HealthArticleTopicDecision.HEALTH_RELATED
        );
    }

    /** Mock 분석 결과 Fixture */
    private HealthAnalysisResult result(ExtractedArticle article) {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        null
                ),
                NOW,
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        article.title(),
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "Mock 분석에서는 근거 검색을 수행하지 않습니다.",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                false
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

    /** 단일 작업과 Lease 상태 Queue */
    private static final class SingleTaskQueue implements HealthAnalysisQueue {

        private HealthAnalysisTask task;
        private final boolean leaseAvailable;
        private int takeCount;
        private int deliveryCount;

        private SingleTaskQueue(HealthAnalysisTask task, boolean leaseAvailable) {
            this.task = task;
            this.leaseAvailable = leaseAvailable;
        }

        @Override
        public boolean enqueue(HealthAnalysisTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HealthAnalysisTask> take() {
            takeCount++;
            HealthAnalysisTask current = task;
            task = null;
            if (current != null) {
                deliveryCount++;
            }
            return Optional.ofNullable(current);
        }

        @Override
        public boolean tryAcquireWorker(String ownerToken, Duration leaseTime) {
            return leaseAvailable;
        }

        @Override
        public void releaseWorker(String ownerToken) {
        }
    }

    /** 단계별 Deadline 이동용 Clock */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 현재 시각 이동 */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
