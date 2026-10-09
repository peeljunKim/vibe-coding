/* 기사 제목 분석 작업 접수와 Polling 구현 */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisRequestRateLimitExceededException;
import com.newsverification.analysis.application.AnalysisRequestRateLimiter;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.monitoring.application.OperationalMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

/** Redis 작업 상태와 제목 전용 Queue 연결 */
@Service
public class DefaultHeadlineAnalysisJobService implements HeadlineAnalysisJobService {

    private final AnalysisJobLifecycleService lifecycleService;
    private final AnalysisJobOutcomeStore outcomeStore;
    private final HeadlineAnalysisQueue queue;
    private final HeadlineAnalysisJobIdentityService identityService;
    private final HeadlineAnalysisUsagePolicy usagePolicy;
    private final AnalysisRequestRateLimiter requestRateLimiter;
    private final ObjectMapper objectMapper;
    private final OperationalMetrics metrics;

    /** 작업 수명·종료 결과·Queue·소유권 구성 */
    @Autowired
    public DefaultHeadlineAnalysisJobService(
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisQueue queue,
            HeadlineAnalysisJobIdentityService identityService,
            HeadlineAnalysisUsagePolicy usagePolicy,
            AnalysisRequestRateLimiter requestRateLimiter,
            ObjectMapper objectMapper,
            ObjectProvider<OperationalMetrics> metrics
    ) {
        this(lifecycleService, outcomeStore, queue, identityService, usagePolicy,
                requestRateLimiter, objectMapper, metrics.getIfAvailable(OperationalMetrics::disabled));
    }

    /** Metric 포함 테스트 구성 */
    DefaultHeadlineAnalysisJobService(
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisQueue queue,
            HeadlineAnalysisJobIdentityService identityService,
            HeadlineAnalysisUsagePolicy usagePolicy,
            AnalysisRequestRateLimiter requestRateLimiter,
            ObjectMapper objectMapper,
            OperationalMetrics metrics
    ) {
        this.lifecycleService = lifecycleService;
        this.outcomeStore = outcomeStore;
        this.queue = queue;
        this.identityService = identityService;
        this.usagePolicy = usagePolicy;
        this.requestRateLimiter = requestRateLimiter;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** Metric 비활성 테스트 구성 */
    DefaultHeadlineAnalysisJobService(
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisQueue queue,
            HeadlineAnalysisJobIdentityService identityService,
            HeadlineAnalysisUsagePolicy usagePolicy,
            AnalysisRequestRateLimiter requestRateLimiter,
            ObjectMapper objectMapper
    ) {
        this(lifecycleService, outcomeStore, queue, identityService, usagePolicy,
                requestRateLimiter, objectMapper, OperationalMetrics.disabled());
    }

    /** 작업 생성 후 제목 전용 Queue 접수 */
    @Override
    public Acceptance accept(String articleUrl, Requester requester) {
        return accept(articleUrl, requester, false);
    }

    /** 기사 변경 동의 표시를 포함한 제목 Queue 접수 */
    @Override
    public Acceptance reanalyze(String articleUrl, Requester requester) {
        return accept(articleUrl, requester, true);
    }

    /** 일반·재분석 제목 작업의 공통 접수 */
    private Acceptance accept(String articleUrl, Requester requester, boolean reanalysisRequested) {
        HeadlineAnalysisJobIdentityService.PreparedIdentity identity = identityService.prepare(requester);
        HeadlineAnalysisUsageResult currentUsage;
        AnalysisJob job = null;
        try {
            requestRateLimiter.acquire(
                    AnalysisRequestRateLimiter.Feature.HEADLINE,
                    identity.usageSubject().identifierKeys()
            );
            currentUsage = usagePolicy.currentUsage(identity.usageSubject());
            job = lifecycleService.accept(UUID.randomUUID().toString(), identity.owner());
            boolean enqueued = queue.enqueue(new HeadlineAnalysisTask(
                    job.id(),
                    articleUrl,
                    identity.usageSubject().userType(),
                    identity.usageSubject().identifierKeys(),
                    reanalysisRequested
            ));
            if (!enqueued) {
                metrics.recordAnalysisRequest(
                        OperationalMetrics.Feature.HEADLINE,
                        OperationalMetrics.RequestOutcome.QUEUE_FULL
                );
                throw new HeadlineAnalysisServiceUnavailableException();
            }
        } catch (RuntimeException exception) {
            if (job != null) {
                rejectAcceptedJob(job.id());
            }
            if (exception instanceof AnalysisRequestRateLimitExceededException rateLimited) {
                throw rateLimited;
            }
            if (exception instanceof HeadlineAnalysisServiceUnavailableException unavailable) {
                throw unavailable;
            }
            metrics.recordAnalysisRequest(
                    OperationalMetrics.Feature.HEADLINE,
                    OperationalMetrics.RequestOutcome.REDIS_UNAVAILABLE
            );
            throw new HeadlineAnalysisServiceUnavailableException(exception);
        }

        metrics.recordAnalysisRequest(
                OperationalMetrics.Feature.HEADLINE,
                OperationalMetrics.RequestOutcome.ACCEPTED
        );

        int limit = currentUsage.dailyLimit();
        int used = currentUsage.usedCount();
        return new Acceptance(
                job.id(), job.status(), job.stage(),
                new Usage(limit, used, Math.max(0, limit - used), false),
                job.acceptedAt(), job.deadlineAt(),
                identity.guestAccessToken(), identity.guestBrowserCookie()
        );
    }

    /** 소유권 확인 후 작업과 종료 결과 조회 */
    @Override
    public Optional<Progress> find(String analysisId, Requester requester) {
        try {
            return identityService.resolveCandidates(requester).stream()
                    .map(owner -> lifecycleService.poll(analysisId, owner))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(this::toProgress);
        } catch (RuntimeException exception) {
            throw new HeadlineAnalysisServiceUnavailableException(exception);
        }
    }

    /** Domain 작업과 저장된 제목 결과 변환 */
    private Progress toProgress(AnalysisJob job) {
        if (job.status() == AnalysisJobStatus.PROCESSING) {
            return new Progress(job.id(), job.status(), job.stage(), job.deadlineAt(), null, null, null, null);
        }
        AnalysisJobOutcome outcome = outcomeStore.findOutcome(job.id())
                .orElseThrow(HeadlineAnalysisServiceUnavailableException::new);
        try {
            Usage usage = outcome.usageJson() == null
                    ? null
                    : objectMapper.readValue(outcome.usageJson(), Usage.class);
            if (outcome.type() == AnalysisJobOutcome.Type.RESULT) {
                return new Progress(
                        job.id(), job.status(), job.stage(), job.deadlineAt(), job.expiresAt(),
                        objectMapper.readValue(outcome.resultJson(), HeadlineAnalysisResult.class),
                        usage, null
                );
            }
            return new Progress(
                    job.id(), job.status(), job.stage(), job.deadlineAt(), job.expiresAt(),
                    null, null, new Failure(outcome.errorCode(), outcome.errorDetail(), usage)
            );
        } catch (JacksonException exception) {
            throw new HeadlineAnalysisServiceUnavailableException(exception);
        }
    }

    /** Queue 수용 실패 작업의 종료 처리 */
    private void rejectAcceptedJob(String jobId) {
        try {
            lifecycleService.fail(jobId);
        } catch (RuntimeException ignored) {
            // Redis 장애 중 최초 실패 원인 보존
        }
    }
}
