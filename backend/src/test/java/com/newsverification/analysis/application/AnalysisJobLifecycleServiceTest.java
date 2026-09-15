/* 비동기 분석 작업 상태 전환 검증 */
package com.newsverification.analysis.application;

import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Clock과 저장 Port 기반 작업 수명 관리 */
class AnalysisJobLifecycleServiceTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-09-14T03:00:00Z");

    /** 대기열 접수 시 기한과 실행 중 안전 만료 설정 */
    @Test
    void acceptsQueuedJobWithDeadlineAndSafetyExpiry() {
        var store = new InMemoryAnalysisJobStore();
        var service = service(store, ACCEPTED_AT);

        AnalysisJob job = service.accept("analysis-1");

        assertThat(job.status()).isEqualTo(AnalysisJobStatus.PROCESSING);
        assertThat(job.stage()).isEqualTo(AnalysisJobStage.QUEUED);
        assertThat(job.acceptedAt()).isEqualTo(ACCEPTED_AT);
        assertThat(job.deadlineAt()).isEqualTo(ACCEPTED_AT.plusSeconds(90));
        assertThat(job.expiresAt()).isEqualTo(ACCEPTED_AT.plus(Duration.ofMinutes(5)));
        assertThat(job.version()).isZero();
    }

    /** 건강 분석 정상 단계 진행과 완료 만료 설정 */
    @Test
    void advancesHealthJobAndCompletesWithTerminalExpiry() {
        var store = new InMemoryAnalysisJobStore();
        service(store, ACCEPTED_AT).accept("analysis-1");

        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);
        service(store, ACCEPTED_AT.plusSeconds(2))
                .advance("analysis-1", AnalysisJobStage.SEARCHING_EVIDENCE);
        service(store, ACCEPTED_AT.plusSeconds(3))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT);
        AnalysisJob completed = service(store, ACCEPTED_AT.plusSeconds(4))
                .complete("analysis-1");

        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(completed.stage()).isEqualTo(AnalysisJobStage.COMPLETED);
        assertThat(completed.deadlineAt()).isEqualTo(ACCEPTED_AT.plusSeconds(90));
        assertThat(completed.expiresAt())
                .isEqualTo(ACCEPTED_AT.plusSeconds(4).plus(Duration.ofMinutes(30)));
        assertThat(completed.version()).isEqualTo(4);
    }

    /** 실패 이후 중복 실패와 완료 역전 차단 */
    @Test
    void keepsFailedJobTerminalWhenLaterCompletionArrives() {
        var store = new InMemoryAnalysisJobStore();
        service(store, ACCEPTED_AT).accept("analysis-1");
        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);

        AnalysisJob failed = service(store, ACCEPTED_AT.plusSeconds(10))
                .fail("analysis-1");
        AnalysisJob repeatedFailure = service(store, ACCEPTED_AT.plusSeconds(20))
                .fail("analysis-1");
        AnalysisJob lateCompletion = service(store, ACCEPTED_AT.plusSeconds(30))
                .complete("analysis-1");

        assertThat(failed.status()).isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(failed.stage()).isEqualTo(AnalysisJobStage.FAILED);
        assertThat(failed.expiresAt())
                .isEqualTo(ACCEPTED_AT.plusSeconds(10).plus(Duration.ofMinutes(30)));
        assertThat(repeatedFailure).isEqualTo(failed);
        assertThat(lateCompletion).isEqualTo(failed);
        assertThat(lateCompletion.version()).isEqualTo(2);
    }

    /** 처리 기한에 도착한 완료 결과 폐기 */
    @Test
    void discardsCompletionAtDeadline() {
        var store = new InMemoryAnalysisJobStore();
        service(store, ACCEPTED_AT).accept("analysis-1");
        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);
        service(store, ACCEPTED_AT.plusSeconds(2))
                .advance("analysis-1", AnalysisJobStage.SEARCHING_EVIDENCE);
        AnalysisJob generating = service(store, ACCEPTED_AT.plusSeconds(3))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT);

        AnalysisJob lateCompletion = service(store, ACCEPTED_AT.plusSeconds(90))
                .complete("analysis-1");

        assertThat(lateCompletion).isEqualTo(generating);
        assertThat(lateCompletion.status()).isEqualTo(AnalysisJobStatus.PROCESSING);
        assertThat(lateCompletion.version()).isEqualTo(3);
    }

    /** 완료 이후 중복 완료의 상태 유지 */
    @Test
    void keepsCompletedJobUnchangedOnDuplicateCompletion() {
        var store = new InMemoryAnalysisJobStore();
        service(store, ACCEPTED_AT).accept("analysis-1");
        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);
        service(store, ACCEPTED_AT.plusSeconds(2))
                .advance("analysis-1", AnalysisJobStage.SEARCHING_EVIDENCE);
        service(store, ACCEPTED_AT.plusSeconds(3))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT);
        AnalysisJob completed = service(store, ACCEPTED_AT.plusSeconds(4))
                .complete("analysis-1");

        AnalysisJob duplicate = service(store, ACCEPTED_AT.plusSeconds(5))
                .complete("analysis-1");

        assertThat(duplicate).isEqualTo(completed);
        assertThat(duplicate.version()).isEqualTo(4);
    }

    /** 실행 중과 terminal 작업의 만료 경계 판정 */
    @Test
    void expiresJobsAtConfiguredRetentionBoundary() {
        var store = new InMemoryAnalysisJobStore();
        AnalysisJob queued = service(store, ACCEPTED_AT).accept("analysis-1");

        assertThat(queued.isExpiredAt(ACCEPTED_AT.plus(Duration.ofMinutes(5)).minusMillis(1)))
                .isFalse();
        assertThat(queued.isExpiredAt(ACCEPTED_AT.plus(Duration.ofMinutes(5))))
                .isTrue();

        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);
        service(store, ACCEPTED_AT.plusSeconds(2))
                .advance("analysis-1", AnalysisJobStage.SEARCHING_EVIDENCE);
        service(store, ACCEPTED_AT.plusSeconds(3))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT);
        AnalysisJob completed = service(store, ACCEPTED_AT.plusSeconds(4))
                .complete("analysis-1");

        assertThat(completed.isExpiredAt(completed.expiresAt().minusMillis(1))).isFalse();
        assertThat(completed.isExpiredAt(completed.expiresAt())).isTrue();
    }

    /** Polling 조회의 상태와 TTL 무변경 */
    @Test
    void pollsWithoutChangingJobOrExtendingExpiry() {
        var store = new InMemoryAnalysisJobStore();
        AnalysisJob accepted = service(store, ACCEPTED_AT).accept("analysis-1");
        int writesAfterAccept = store.writeCount();

        Optional<AnalysisJob> polled = service(store, ACCEPTED_AT.plusSeconds(30))
                .poll("analysis-1");
        Optional<AnalysisJob> expired = service(
                store,
                ACCEPTED_AT.plus(Duration.ofMinutes(5))
        ).poll("analysis-1");

        assertThat(polled).contains(accepted);
        assertThat(expired).isEmpty();
        assertThat(store.writeCount()).isEqualTo(writesAfterAccept);
    }

    /** 제목 분석의 근거 검색 단계 생략 허용 */
    @Test
    void allowsHeadlineFlowToSkipEvidenceStage() {
        var store = new InMemoryAnalysisJobStore();
        service(store, ACCEPTED_AT).accept("analysis-1");
        service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.CHECKING_ARTICLE);

        AnalysisJob generating = service(store, ACCEPTED_AT.plusSeconds(2))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT);

        assertThat(generating.stage()).isEqualTo(AnalysisJobStage.GENERATING_RESULT);
        assertThat(generating.version()).isEqualTo(2);
    }

    /** 필수 선행 단계를 건너뛴 전환 차단 */
    @Test
    void rejectsInvalidStageTransition() {
        var store = new InMemoryAnalysisJobStore();
        AnalysisJob queued = service(store, ACCEPTED_AT).accept("analysis-1");

        assertThatThrownBy(() -> service(store, ACCEPTED_AT.plusSeconds(1))
                .advance("analysis-1", AnalysisJobStage.GENERATING_RESULT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service(store, ACCEPTED_AT.plusSeconds(2)).poll("analysis-1"))
                .contains(queued);
    }

    /** 고정 시점 작업 수명 Service 구성 */
    private AnalysisJobLifecycleService service(
            AnalysisJobStore store,
            Instant currentTime
    ) {
        Clock clock = Clock.fixed(currentTime, ZoneOffset.UTC);
        return new AnalysisJobLifecycleService(store, clock);
    }

    /** Redis 대체용 조건부 저장 Mock */
    private static final class InMemoryAnalysisJobStore implements AnalysisJobStore {

        private final Map<String, AnalysisJob> jobs = new HashMap<>();
        private int writeCount;

        @Override
        public boolean create(AnalysisJob job) {
            boolean created = jobs.putIfAbsent(job.id(), job) == null;
            if (created) {
                writeCount++;
            }
            return created;
        }

        @Override
        public Optional<AnalysisJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public boolean replace(String jobId, long expectedVersion, AnalysisJob updatedJob) {
            boolean replaced = jobs.computeIfPresent(jobId, (id, currentJob) ->
                    currentJob.version() == expectedVersion ? updatedJob : currentJob
            ) == updatedJob;
            if (replaced) {
                writeCount++;
            }
            return replaced;
        }

        /** 생성과 상태 교체 횟수 */
        private int writeCount() {
            return writeCount;
        }
    }
}
