/* 기사 제목 분석 Worker 검증 */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;
import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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

/** 근거 검색과 건강 분야 판별 없는 단계 전환 검증 */
class HeadlineAnalysisWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");

    /** 기사 수집 후 이용량 차감과 제목 결과 완료 */
    @Test
    void completesHeadlineAnalysisWithoutEvidenceStage() {
        InMemoryStore store = new InMemoryStore();
        AnalysisJob job = AnalysisJob.queued(
                "headline-1",
                new AnalysisJobOwner(AnalysisJobOwnerType.MEMBER, "owner-hash"),
                NOW
        );
        store.create(job);
        HeadlineAnalysisTask task = new HeadlineAnalysisTask(
                job.id(),
                "https://news.example/general",
                HeadlineAnalysisUserType.MEMBER,
                List.of("member-key")
        );
        RecordingQueue queue = new RecordingQueue(task);
        HeadlineAnalysisUseCase useCase = mock(HeadlineAnalysisUseCase.class);
        HeadlineAnalysisUsagePolicy usagePolicy = mock(HeadlineAnalysisUsagePolicy.class);
        ExtractedArticle article = article();
        HeadlineAnalysisResult result = result(article);
        when(useCase.read(task.articleUrl())).thenReturn(article);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HeadlineAnalysisUsageResult(1, 10));
        when(useCase.analyze(article, job.deadlineAt())).thenReturn(result);

        HeadlineAnalysisWorker worker = new HeadlineAnalysisWorker(
                queue,
                new AnalysisJobLifecycleService(store, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC)),
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC),
                "worker-1"
        );

        assertThat(worker.runOnce()).isTrue();
        AnalysisJob completed = store.findById(job.id()).orElseThrow();
        assertThat(completed.status().name()).isEqualTo("COMPLETED");
        assertThat(completed.stage().name()).isEqualTo("COMPLETED");
        assertThat(store.findOutcome(job.id())).isPresent();
        verify(usagePolicy).verifyCanStart(task.usageSubject());
        verify(usagePolicy).recordAnalysisStart(task.usageSubject());
        verify(useCase).read(task.articleUrl());
        verify(useCase).analyze(article, job.deadlineAt());
    }

    /** 기사 수집 실패의 무차감 종료 */
    @Test
    void doesNotChargeWhenArticleExtractionFails() {
        InMemoryStore store = new InMemoryStore();
        AnalysisJob job = AnalysisJob.queued(
                "headline-failed",
                new AnalysisJobOwner(AnalysisJobOwnerType.GUEST, "owner-hash"),
                NOW
        );
        store.create(job);
        HeadlineAnalysisTask task = new HeadlineAnalysisTask(
                job.id(),
                "https://news.example/unavailable",
                HeadlineAnalysisUserType.GUEST,
                List.of("cookie-key", "ip-key")
        );
        HeadlineAnalysisUseCase useCase = mock(HeadlineAnalysisUseCase.class);
        HeadlineAnalysisUsagePolicy usagePolicy = mock(HeadlineAnalysisUsagePolicy.class);
        when(useCase.read(task.articleUrl()))
                .thenThrow(new ArticleProcessingException(ArticleProcessingError.DOWNLOAD_FAILED));
        Clock clock = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
        HeadlineAnalysisWorker worker = new HeadlineAnalysisWorker(
                new RecordingQueue(task),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                clock,
                "worker-1"
        );

        assertThat(worker.runOnce()).isTrue();
        assertThat(store.findById(job.id()).orElseThrow().status().name()).isEqualTo("FAILED");
        assertThat(store.findOutcome(job.id()).orElseThrow().errorCode()).isEqualTo("DOWNLOAD_FAILED");
        verify(usagePolicy).verifyCanStart(task.usageSubject());
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
    }

    /** 정제 기사 Fixture */
    private ExtractedArticle article() {
        return new ExtractedArticle(
                URI.create("https://news.example/general"),
                "기존 기사 제목",
                "일반 기사 본문",
                OffsetDateTime.parse("2026-09-19T09:00:00+09:00"),
                Optional.empty()
        );
    }

    /** 제목 분석 결과 Fixture */
    private HeadlineAnalysisResult result(ExtractedArticle article) {
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        null
                ),
                NOW.plusSeconds(1),
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE,
                        "제목과 본문의 핵심 내용이 일치합니다."
                )),
                null
        );
    }

    /** 테스트용 작업과 결과 저장소 */
    private static final class InMemoryStore implements AnalysisJobStore, AnalysisJobOutcomeStore {

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

    /** 단일 작업 Queue */
    private static final class RecordingQueue implements HeadlineAnalysisQueue {

        private HeadlineAnalysisTask task;

        private RecordingQueue(HeadlineAnalysisTask task) {
            this.task = task;
        }

        @Override
        public boolean enqueue(HeadlineAnalysisTask task) {
            return false;
        }

        @Override
        public Optional<HeadlineAnalysisTask> take() {
            HeadlineAnalysisTask current = task;
            task = null;
            return Optional.ofNullable(current);
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
