/* 기사 제목 분석 작업 접수와 Polling 구현 */
package com.newsverification.headline.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStatus;
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
    private final ObjectMapper objectMapper;

    /** 작업 수명·종료 결과·Queue·소유권 구성 */
    public DefaultHeadlineAnalysisJobService(
            AnalysisJobLifecycleService lifecycleService,
            AnalysisJobOutcomeStore outcomeStore,
            HeadlineAnalysisQueue queue,
            HeadlineAnalysisJobIdentityService identityService,
            ObjectMapper objectMapper
    ) {
        this.lifecycleService = lifecycleService;
        this.outcomeStore = outcomeStore;
        this.queue = queue;
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    /** 작업 생성 후 제목 전용 Queue 접수 */
    @Override
    public Acceptance accept(String articleUrl, Requester requester) {
        HeadlineAnalysisJobIdentityService.PreparedIdentity identity = identityService.prepare(requester);
        AnalysisJob job;
        try {
            job = lifecycleService.accept(UUID.randomUUID().toString(), identity.owner());
            boolean enqueued = queue.enqueue(new HeadlineAnalysisTask(
                    job.id(),
                    articleUrl,
                    identity.usageSubject().userType(),
                    identity.usageSubject().identifierKeys()
            ));
            if (!enqueued) {
                rejectAcceptedJob(job.id());
                throw new HeadlineAnalysisServiceUnavailableException();
            }
        } catch (HeadlineAnalysisServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new HeadlineAnalysisServiceUnavailableException(exception);
        }

        int limit = identity.usageSubject().userType().dailyLimit();
        return new Acceptance(
                job.id(), job.status(), job.stage(),
                new Usage(limit, 0, limit, false),
                job.acceptedAt(), job.deadlineAt(),
                identity.guestAccessToken(), identity.guestBrowserCookie()
        );
    }

    /** 소유권 확인 후 작업과 종료 결과 조회 */
    @Override
    public Optional<Progress> find(String analysisId, Requester requester) {
        try {
            return identityService.resolve(requester)
                    .flatMap(owner -> lifecycleService.poll(analysisId, owner))
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
