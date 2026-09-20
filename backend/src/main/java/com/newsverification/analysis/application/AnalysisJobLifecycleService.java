/* 비동기 분석 작업 수명 관리 */
package com.newsverification.analysis.application;

import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobStage;

import java.time.Clock;
import java.util.ConcurrentModificationException;
import java.util.Optional;

/** Clock과 저장 Port 기반 상태 전환 진입점 */
public class AnalysisJobLifecycleService {

    private final AnalysisJobStore store;
    private final Clock clock;

    /** 작업 저장소와 기준 시각 구성 */
    public AnalysisJobLifecycleService(AnalysisJobStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** 대기열 접수 작업 생성 */
    public AnalysisJob accept(String jobId, AnalysisJobOwner owner) {
        AnalysisJob job = AnalysisJob.queued(jobId, owner, clock.instant());
        if (!store.create(job)) {
            throw new IllegalStateException("Analysis job already exists");
        }
        return job;
    }

    /** 작업 진행 단계 전환 */
    public AnalysisJob advance(String jobId, AnalysisJobStage nextStage) {
        AnalysisJob currentJob = requireJob(jobId);
        return replaceIfChanged(currentJob, currentJob.advanceTo(nextStage, clock.instant()));
    }

    /** 작업 완료 전환 */
    public AnalysisJob complete(String jobId) {
        AnalysisJob currentJob = requireJob(jobId);
        return replaceIfChanged(currentJob, currentJob.complete(clock.instant()));
    }

    /** 작업 실패 전환 */
    public AnalysisJob fail(String jobId) {
        AnalysisJob currentJob = requireJob(jobId);
        return replaceIfChanged(currentJob, currentJob.fail(clock.instant()));
    }

    /** 상태와 TTL을 변경하지 않는 Polling 조회 */
    public Optional<AnalysisJob> poll(String jobId, AnalysisJobOwner owner) {
        return store.findById(jobId)
                .filter(job -> job.owner().equals(owner))
                .filter(job -> !job.isExpiredAt(clock.instant()));
    }

    /** 필수 작업 조회 */
    private AnalysisJob requireJob(String jobId) {
        return store.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis job not found"));
    }

    /** 변경된 상태의 조건부 저장 */
    private AnalysisJob replaceIfChanged(AnalysisJob currentJob, AnalysisJob updatedJob) {
        if (currentJob.equals(updatedJob)) {
            return currentJob;
        }
        if (!store.replace(currentJob.id(), currentJob.version(), updatedJob)) {
            throw new ConcurrentModificationException("Analysis job changed concurrently");
        }
        return updatedJob;
    }
}
