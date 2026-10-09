/* 건강 분석 Background Worker */
package com.newsverification.health.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.AnalysisCacheKeyFactory;
import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import com.newsverification.analysiscache.application.AnalysisCacheViewer;
import com.newsverification.analysiscache.application.ArticleRevisionFingerprint;
import com.newsverification.analysiscache.application.CachedAnalysisResult;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.monitoring.application.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
    private final HealthAnalysisResultCache resultCache;
    private final AnalysisCacheVersions cacheVersions;
    private final HealthEvidenceLinkValidationService evidenceLinkValidationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String leaseOwnerId;
    private final OperationalMetrics metrics;

    /** Queue·상태·분석·이용량 경계 구성 */
    @Autowired
    public HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            HealthAnalysisResultCache resultCache,
            AnalysisCacheVersions cacheVersions,
            HealthEvidenceLinkValidationService evidenceLinkValidationService,
            ObjectMapper objectMapper,
            Clock clock,
            ObjectProvider<OperationalMetrics> metrics
    ) {
        this(
                queue,
                lifecycleService,
                jobStore,
                outcomeStore,
                useCase,
                usagePolicy,
                resultCache,
                cacheVersions,
                evidenceLinkValidationService,
                objectMapper,
                clock,
                UUID.randomUUID().toString(),
                metrics.getIfAvailable(OperationalMetrics::disabled)
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
        this(
                queue,
                lifecycleService,
                jobStore,
                outcomeStore,
                useCase,
                usagePolicy,
                HealthAnalysisResultCache.disabled(),
                AnalysisCacheVersions.mockDefaults(),
                HealthEvidenceLinkValidationService.trustAll(),
                objectMapper,
                clock,
                leaseOwnerId,
                OperationalMetrics.disabled()
        );
    }

    /** Cache 경계를 포함한 테스트 제어용 Worker Token 구성 */
    HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            HealthAnalysisResultCache resultCache,
            AnalysisCacheVersions cacheVersions,
            ObjectMapper objectMapper,
            Clock clock,
            String leaseOwnerId
    ) {
        this(
                queue,
                lifecycleService,
                jobStore,
                outcomeStore,
                useCase,
                usagePolicy,
                resultCache,
                cacheVersions,
                HealthEvidenceLinkValidationService.trustAll(),
                objectMapper,
                clock,
                leaseOwnerId,
                OperationalMetrics.disabled()
        );
    }

    /** Cache와 근거 링크 검증을 포함한 테스트 제어용 구성 */
    HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            HealthAnalysisResultCache resultCache,
            AnalysisCacheVersions cacheVersions,
            HealthEvidenceLinkValidationService evidenceLinkValidationService,
            ObjectMapper objectMapper,
            Clock clock,
            String leaseOwnerId
    ) {
        this(queue, lifecycleService, jobStore, outcomeStore, useCase, usagePolicy,
                resultCache, cacheVersions, evidenceLinkValidationService, objectMapper,
                clock, leaseOwnerId, OperationalMetrics.disabled());
    }

    /** 업무 Metric을 포함한 Worker 구성 */
    HealthAnalysisWorker(
            HealthAnalysisQueue queue,
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobStore jobStore,
            AnalysisJobOutcomeStore outcomeStore,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            HealthAnalysisResultCache resultCache,
            AnalysisCacheVersions cacheVersions,
            HealthEvidenceLinkValidationService evidenceLinkValidationService,
            ObjectMapper objectMapper,
            Clock clock,
            String leaseOwnerId,
            OperationalMetrics metrics
    ) {
        this.queue = queue;
        this.lifecycleService = lifecycleService;
        this.jobStore = jobStore;
        this.outcomeStore = outcomeStore;
        this.useCase = useCase;
        this.usagePolicy = usagePolicy;
        this.resultCache = resultCache;
        this.cacheVersions = cacheVersions;
        this.evidenceLinkValidationService = evidenceLinkValidationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.leaseOwnerId = leaseOwnerId;
        this.metrics = metrics;
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
            long startedAt = System.nanoTime();
            try {
                process(task.orElseThrow());
            } finally {
                metrics.recordWorkerDuration(
                        OperationalMetrics.Feature.HEALTH,
                        Duration.ofNanos(System.nanoTime() - startedAt)
                );
            }
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
        HealthAnalysisJobService.Usage maintenanceUsage = null;
        HealthAnalysisResult limitedFallback = null;
        ExtractedArticle currentArticle = null;
        Optional<AnalysisCacheKey> cacheKey = Optional.empty();
        boolean automaticEvidenceRefresh = false;
        try {
            cacheKey = AnalysisCacheKeyFactory.health(
                    task.articleUrl(),
                    cacheVersions
            );
            if (cacheKey.isPresent()) {
                Optional<CachedAnalysisResult<HealthAnalysisResult>> cached = resultCache
                        .findHealth(cacheKey.orElseThrow());
                metrics.recordCacheLookup(
                        OperationalMetrics.Feature.HEALTH,
                        cached.isPresent()
                                ? OperationalMetrics.CacheOutcome.HIT
                                : OperationalMetrics.CacheOutcome.MISS
                );
                if (cached.isPresent()) {
                    CachedAnalysisResult<HealthAnalysisResult> cachedResult = cached.orElseThrow();
                    currentArticle = useCase.read(task.articleUrl());
                    boolean articleChanged = !cachedResult.articleFingerprint().equals(
                            ArticleRevisionFingerprint.from(currentArticle)
                    );
                    if (articleChanged && !task.reanalysisRequested()) {
                        fail(
                                task.analysisId(),
                                "ARTICLE_CHANGED",
                                "기사 내용이 분석 당시와 달라졌습니다. 최신 내용으로 다시 분석하시겠습니까?",
                                null
                        );
                        return;
                    }
                    if (!articleChanged) {
                        HealthEvidenceLinkValidation validation = evidenceLinkValidationService
                                .validate(cachedResult.result());
                        if (validation.status() == HealthEvidenceLinkValidation.Status.MISSING) {
                            automaticEvidenceRefresh = true;
                            limitedFallback = validation.limitedResult().orElseThrow();
                            maintenanceUsage = toUsage(usagePolicy.currentUsage(task.usageSubject()));
                            resultCache.evictHealth(cacheKey.orElseThrow());
                        } else {
                            String viewerFingerprint = AnalysisCacheViewer.fingerprint(
                                    task.userType().name(),
                                    task.usageIdentifierKeys()
                            );
                            Optional<HealthTopicFailureUsageResult> cacheUsage = usagePolicy
                                    .recordCacheAccess(
                                            task.usageSubject(),
                                            cacheKey.orElseThrow(),
                                            viewerFingerprint
                                    );
                            if (cacheUsage.isPresent()) {
                                HealthAnalysisJobService.Usage usage = toUsage(cacheUsage.orElseThrow());
                                chargedUsage = usage;
                                completeCached(
                                        task.analysisId(),
                                        cachedResult.result(),
                                        usage
                                );
                                return;
                            }
                        }
                    }
                }
            }

            AnalysisJob checkingJob = lifecycleService.advance(
                    task.analysisId(),
                    AnalysisJobStage.CHECKING_ARTICLE
            );
            HealthArticleScreeningResult screening = automaticEvidenceRefresh
                    ? useCase.screenForMaintenance(currentArticle)
                    : currentArticle == null
                            ? useCase.screen(task.articleUrl(), task.usageSubject())
                            : useCase.screen(currentArticle, task.usageSubject());
            if (screening.decision() != HealthArticleTopicDecision.HEALTH_RELATED) {
                if (automaticEvidenceRefresh && completeLimitedFallback(
                        task.analysisId(),
                        limitedFallback,
                        maintenanceUsage
                )) {
                    cacheResult(cacheKey, task, limitedFallback, currentArticle);
                    return;
                }
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
            HealthAnalysisJobService.Usage usage = automaticEvidenceRefresh
                    ? maintenanceUsage
                    : toUsage(usagePolicy.recordAnalysisStart(task.usageSubject()));
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
            if (complete(task.analysisId(), result, usage)) {
                cacheResult(cacheKey, task, result, screening.article());
            }
        } catch (ArticleProcessingException exception) {
            if (automaticEvidenceRefresh && completeLimitedFallback(
                    task.analysisId(),
                    limitedFallback,
                    maintenanceUsage
            )) {
                cacheResult(cacheKey, task, limitedFallback, currentArticle);
                return;
            }
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
            if (automaticEvidenceRefresh && completeLimitedFallback(
                    task.analysisId(),
                    limitedFallback,
                    maintenanceUsage
            )) {
                LOGGER.warn(
                        "Automatic evidence refresh failed: {}",
                        exception.getClass().getSimpleName()
                );
                cacheResult(cacheKey, task, limitedFallback, currentArticle);
                return;
            }
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
    private boolean complete(
            String analysisId,
            HealthAnalysisResult result,
            HealthAnalysisJobService.Usage usage
    ) {
        AnalysisJob current = jobStore.findById(analysisId).orElseThrow();
        AnalysisJob completed = current.complete(clock.instant());
        if (completed.equals(current)) {
            fail(analysisId, "ANALYSIS_DEADLINE_EXCEEDED", "분석 제한 시간을 초과했습니다.", usage);
            return false;
        }
        boolean stored = outcomeStore.replaceWithOutcome(
                analysisId,
                current.version(),
                completed,
                AnalysisJobOutcome.completed(serialize(result), serialize(usage))
        );
        if (stored) {
            metrics.recordWorkerResult(
                    OperationalMetrics.Feature.HEALTH,
                    OperationalMetrics.WorkerOutcome.COMPLETED
            );
        }
        return stored;
    }

    /** 외부 처리 없는 Cache 결과 단계 전환과 완료 */
    private void completeCached(
            String analysisId,
            HealthAnalysisResult result,
            HealthAnalysisJobService.Usage usage
    ) {
        lifecycleService.advance(analysisId, AnalysisJobStage.CHECKING_ARTICLE);
        lifecycleService.advance(analysisId, AnalysisJobStage.SEARCHING_EVIDENCE);
        lifecycleService.advance(analysisId, AnalysisJobStage.GENERATING_RESULT);
        complete(analysisId, result, usage);
    }

    /** 현재 단계에서 제한 결과 완료 단계까지 전환 */
    private boolean completeLimitedFallback(
            String analysisId,
            HealthAnalysisResult limitedResult,
            HealthAnalysisJobService.Usage usage
    ) {
        if (limitedResult == null || usage == null) {
            return false;
        }
        try {
            AnalysisJob current = jobStore.findById(analysisId).orElseThrow();
            if (current.stage() == AnalysisJobStage.QUEUED) {
                current = lifecycleService.advance(analysisId, AnalysisJobStage.CHECKING_ARTICLE);
            }
            if (current.stage() == AnalysisJobStage.CHECKING_ARTICLE
                    || current.stage() == AnalysisJobStage.SEARCHING_EVIDENCE) {
                lifecycleService.advance(analysisId, AnalysisJobStage.GENERATING_RESULT);
            }
            return complete(analysisId, limitedResult, usage);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 현재 Version 일치 결과의 Cache 저장 */
    private void cacheResult(
            Optional<AnalysisCacheKey> cacheKey,
            HealthAnalysisTask task,
            HealthAnalysisResult result,
            ExtractedArticle article
    ) {
        if (cacheKey.isEmpty() || !cacheVersions.matchesHealth(
                result.aiModelVersion(),
                result.policyVersion(),
                result.evidenceAllowlistVersion()
        )) {
            return;
        }
        try {
            resultCache.saveHealth(
                    cacheKey.orElseThrow(),
                    result,
                    ArticleRevisionFingerprint.from(article),
                    AnalysisCacheViewer.fingerprint(
                            task.userType().name(),
                            task.usageIdentifierKeys()
                    )
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Health analysis cache write failed: {}", exception.getClass().getSimpleName());
        }
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
        boolean stored = outcomeStore.replaceWithOutcome(
                analysisId,
                current.version(),
                failed,
                AnalysisJobOutcome.failed(code, detail, usage == null ? null : serialize(usage))
        );
        if (stored) {
            metrics.recordWorkerResult(
                    OperationalMetrics.Feature.HEALTH,
                    OperationalMetrics.WorkerOutcome.FAILED
            );
        }
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
