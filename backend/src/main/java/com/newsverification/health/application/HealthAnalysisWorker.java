/* 건강 분석 Background Worker */
package com.newsverification.health.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.article.domain.ArticleProcessingException;
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

/** 전역 Lease와 단일 소비 기반 무재시도 Worker */
@Component
public class HealthAnalysisWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthAnalysisWorker.class);
    private static final Duration WORKER_LEASE = Duration.ofSeconds(95);

    private final HealthAnalysisQueue queue;
    private final AnalysisJobLifecycleService lifecycleService;
    private final AnalysisJobStore jobStore;
    private final AnalysisJobOutcomeStore outcomeStore;
    private final HealthAnalysisUseCase useCase;
    private final HealthTopicFailureUsagePolicy usagePolicy;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String leaseOwnerId;

    /** Queue·상태·분석·이용량 경계 구성 */
    @Autowired
    public HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                queue,
                lifecycleService,
                jobStore,
                outcomeStore,
                useCase,
                usagePolicy,
                objectMapper,
                clock,
                UUID.randomUUID().toString()
        );
    }

    /** 테스트 제어용 Worker Token 포함 구성 */
    HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
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

    /** 단일 작업 Polling과 비민감 장애 기록 */
    @Scheduled(fixedDelayString = "${app.analysis.worker.poll-delay:500ms}")
    public void pollQueue() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn("Health analysis worker cycle failed: {}", exception.getClass().getSimpleName());
        }
    }

    /** 전역 Lease 안의 최대 한 작업 실행 */
    boolean runOnce() {
        if (!queue.tryAcquireWorker(leaseOwnerId, WORKER_LEASE)) {
            return false;
        }
        try {
            Optional<HealthAnalysisTask> task = queue.take();
            if (task.isEmpty()) {
                return false;
            }
            process(task.orElseThrow());
            return true;
        } finally {
            queue.releaseWorker(leaseOwnerId);
        }
    }

    /** 단계 전환과 기사 검증·분야 판별·Mock 분석 연결 */
    private void process(HealthAnalysisTask task) {
        AnalysisJob queuedJob = jobStore.findById(task.analysisId()).orElse(null);
        if (queuedJob == null || queuedJob.status() != AnalysisJobStatus.PROCESSING) {
            return;
        }
        if (!clock.instant().isBefore(queuedJob.deadlineAt())) {
            fail(task.analysisId(), "ANALYSIS_DEADLINE_EXCEEDED", "분석 제한 시간을 초과했습니다.", null);
            return;
        }

        HealthAnalysisJobService.Usage chargedUsage = null;
        try {
            AnalysisJob checkingJob = lifecycleService.advance(
                    task.analysisId(),
                    AnalysisJobStage.CHECKING_ARTICLE
            );
            HealthArticleScreeningResult screening = useCase.screen(
                    task.articleUrl(),
                    task.usageSubject()
            );
            if (screening.decision() != HealthArticleTopicDecision.HEALTH_RELATED) {
                HealthAnalysisRoutingResult stopped = useCase.continueAfterScreening(
                        screening,
                        task.usageSubject(),
                        checkingJob.deadlineAt()
                );
                fail(
                        task.analysisId(),
                        failureCode(stopped.status()),
                        stopped.userMessage().orElse("건강·의학 기사 여부를 확인하지 못했습니다."),
                        null
                );
                return;
            }

            AnalysisJob evidenceJob = lifecycleService.advance(
                    task.analysisId(),
                    AnalysisJobStage.SEARCHING_EVIDENCE
            );
            if (evidenceJob.stage() != AnalysisJobStage.SEARCHING_EVIDENCE) {
                fail(
                        task.analysisId(),
                        "ANALYSIS_DEADLINE_EXCEEDED",
                        "분석 제한 시간을 초과했습니다.",
                        null
                );
                return;
            }
            HealthTopicFailureUsageResult chargedUsageResult = usagePolicy.recordAnalysisStart(
                    task.usageSubject()
            );
            HealthAnalysisJobService.Usage usage = toUsage(chargedUsageResult);
            chargedUsage = usage;
            HealthAnalysisRoutingResult routing = useCase.continueAfterScreening(
                    screening,
                    task.usageSubject(),
                    evidenceJob.deadlineAt()
            );
            HealthAnalysisResult result = routing.result()
                    .orElseThrow(() -> new IllegalStateException("Health analysis result is missing"));
            AnalysisJob generatingJob = lifecycleService.advance(
                    task.analysisId(),
                    AnalysisJobStage.GENERATING_RESULT
            );
            if (generatingJob.stage() != AnalysisJobStage.GENERATING_RESULT) {
                fail(
                        task.analysisId(),
                        "ANALYSIS_DEADLINE_EXCEEDED",
                        "분석 제한 시간을 초과했습니다.",
                        usage
                );
                return;
            }
            complete(task.analysisId(), result, usage);
        } catch (ArticleProcessingException exception) {
            fail(
                    task.analysisId(),
                    exception.error().name(),
                    "기사 내용을 확인하지 못했습니다.",
                    null
            );
        } catch (HealthDailyUsageLimitExceededException exception) {
            fail(
                    task.analysisId(),
                    "DAILY_LIMIT_EXCEEDED",
                    "오늘 사용할 수 있는 분석 횟수를 모두 사용했습니다.",
                    new HealthAnalysisJobService.Usage(
                            exception.dailyLimit(),
                            exception.usedCount(),
                            0,
                            false
                    )
            );
        } catch (RuntimeException exception) {
            LOGGER.error("Health analysis failed. analysisId={}", task.analysisId(), exception);
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
            HealthAnalysisResult result,
            HealthAnalysisJobService.Usage usage
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
            HealthAnalysisJobService.Usage usage
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

    /** 분야 판별 중단의 오류 코드 변환 */
    private String failureCode(HealthAnalysisRoutingStatus status) {
        return switch (status) {
            case NOT_HEALTH_ARTICLE -> "ARTICLE_NOT_HEALTH_RELATED";
            case TOPIC_UNCERTAIN -> "ARTICLE_TOPIC_UNCERTAIN";
            case ANALYSIS_STARTED -> "ANALYSIS_FAILED";
        };
    }

    /** 이용량 결과의 API 값 변환 */
    private HealthAnalysisJobService.Usage toUsage(HealthTopicFailureUsageResult result) {
        return new HealthAnalysisJobService.Usage(
                result.dailyLimit(),
                result.usedCount(),
                Math.max(0, result.dailyLimit() - result.usedCount()),
                result.charged()
        );
    }

    /** 종료 결과 JSON 직렬화 */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Analysis outcome serialization failed", exception);
        }
    }
}
