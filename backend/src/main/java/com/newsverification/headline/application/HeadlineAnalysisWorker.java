/* 기사 제목 분석 Background Worker */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** 제목 전용 단일 소비와 무재시도 Worker */
@Component
public class HeadlineAnalysisWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeadlineAnalysisWorker.class);
    private static final Duration WORKER_LEASE = Duration.ofSeconds(95);

    private final HeadlineAnalysisQueue queue;
    private final AnalysisJobLifecycleService lifecycleService;
    private final AnalysisJobStore jobStore;
    private final AnalysisJobOutcomeStore outcomeStore;
    private final HeadlineAnalysisUseCase useCase;
    private final HeadlineAnalysisUsagePolicy usagePolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String leaseOwnerId;

    /** Queue·상태·제목 분석·이용량 구성 */
    @Autowired
    public HeadlineAnalysisWorker(
            HeadlineAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisUseCase useCase,
            HeadlineAnalysisUsagePolicy usagePolicy,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(queue, lifecycleService, jobStore, outcomeStore, useCase, usagePolicy,
                objectMapper, clock, UUID.randomUUID().toString());
    }

    /** 테스트 제어용 Worker Token 포함 구성 */
    HeadlineAnalysisWorker(
            HeadlineAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisUseCase useCase,
            HeadlineAnalysisUsagePolicy usagePolicy,
            ObjectMapper objectMapper,
            Clock clock,
            String leaseOwnerId
    ) {
        this.queue = queue;
        this.lifecycleService = lifecycleService;
        this.jobStore = jobStore;
        this.outcomeStore = outcomeStore;
        this.useCase = useCase;
        this.usagePolicy = usagePolicy;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.leaseOwnerId = leaseOwnerId;
    }

    /** 제목 Queue의 단일 작업 Polling */
    @Scheduled(fixedDelayString = "${app.analysis.worker.poll-delay:500ms}")
    public void pollQueue() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn("Headline analysis worker cycle failed: {}", exception.getClass().getSimpleName());
        }
    }

    /** 제목 Worker Lease 안의 최대 한 작업 실행 */
    boolean runOnce() {
        if (!queue.tryAcquireWorker(leaseOwnerId, WORKER_LEASE)) {
            return false;
        }
        try {
            Optional<HeadlineAnalysisTask> task = queue.take();
            if (task.isEmpty()) {
                return false;
            }
            process(task.orElseThrow());
            return true;
        } finally {
            queue.releaseWorker(leaseOwnerId);
        }
    }

    /** 기사 수집 후 근거 검색 없는 제목 분석 처리 */
    private void process(HeadlineAnalysisTask task) {
        AnalysisJob queuedJob = jobStore.findById(task.analysisId()).orElse(null);
        if (queuedJob == null || queuedJob.status() != AnalysisJobStatus.PROCESSING) {
            return;
        }
        if (!clock.instant().isBefore(queuedJob.deadlineAt())) {
            fail(task.analysisId(), "ANALYSIS_DEADLINE_EXCEEDED", "분석 제한 시간을 초과했습니다.", null);
            return;
        }

        HeadlineAnalysisJobService.Usage chargedUsage = null;
        try {
            usagePolicy.verifyCanStart(task.usageSubject());
            AnalysisJob checkingJob = lifecycleService.advance(
                    task.analysisId(), AnalysisJobStage.CHECKING_ARTICLE
            );
            ExtractedArticle article = useCase.read(task.articleUrl());
            AnalysisJob generatingJob = lifecycleService.advance(
                    task.analysisId(), AnalysisJobStage.GENERATING_RESULT
            );
            if (generatingJob.stage() != AnalysisJobStage.GENERATING_RESULT) {
                fail(task.analysisId(), "ANALYSIS_DEADLINE_EXCEEDED", "분석 제한 시간을 초과했습니다.", null);
                return;
            }
            HeadlineAnalysisUsageResult charged = usagePolicy.recordAnalysisStart(task.usageSubject());
            HeadlineAnalysisJobService.Usage usage = new HeadlineAnalysisJobService.Usage(
                    charged.dailyLimit(), charged.usedCount(),
                    Math.max(0, charged.dailyLimit() - charged.usedCount()), true
            );
            chargedUsage = usage;
            HeadlineAnalysisResult result = useCase.analyze(article, checkingJob.deadlineAt());
            complete(task.analysisId(), result, usage);
        } catch (ArticleProcessingException exception) {
            fail(task.analysisId(), exception.error().name(), "기사 내용을 확인하지 못했습니다.", null);
        } catch (HeadlineDailyUsageLimitExceededException exception) {
            fail(
                    task.analysisId(),
                    "DAILY_LIMIT_EXCEEDED",
                    "오늘 사용할 수 있는 제목 분석 횟수를 모두 사용했습니다.",
                    new HeadlineAnalysisJobService.Usage(
                            exception.dailyLimit(), exception.usedCount(), 0, false
                    )
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Headline analysis failed. analysisId={}", task.analysisId(), exception);
            fail(
                    task.analysisId(),
                    "ANALYSIS_FAILED",
                    "분석하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    chargedUsage
            );
        }
    }

    /** 결과와 완료 상태의 원자 저장 */
    private void complete(
            String analysisId,
            HeadlineAnalysisResult result,
            HeadlineAnalysisJobService.Usage usage
    ) {
        AnalysisJob current = jobStore.findById(analysisId).orElseThrow();
        AnalysisJob completed = current.complete(clock.instant());
        if (completed.equals(current)) {
            fail(analysisId, "ANALYSIS_DEADLINE_EXCEEDED", "분석 제한 시간을 초과했습니다.", usage);
            return;
        }
        outcomeStore.replaceWithOutcome(
                analysisId,
                current.version(),
                completed,
                AnalysisJobOutcome.completed(serialize(result), serialize(usage))
        );
    }

    /** 실패와 오류 응답의 원자 저장 */
    private void fail(
            String analysisId,
            String code,
            String detail,
            HeadlineAnalysisJobService.Usage usage
    ) {
        AnalysisJob current = jobStore.findById(analysisId).orElse(null);
        if (current == null || current.status() != AnalysisJobStatus.PROCESSING) {
            return;
        }
        AnalysisJob failed = current.fail(clock.instant());
        outcomeStore.replaceWithOutcome(
                analysisId,
                current.version(),
                failed,
                AnalysisJobOutcome.failed(code, detail, usage == null ? null : serialize(usage))
        );
    }

    /** 종료 결과 JSON 직렬화 */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Headline outcome serialization failed", exception);
        }
    }
}
