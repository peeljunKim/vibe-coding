/* 저장 건강 분석 Use Case 검증 */
package com.newsverification.healthrecord.application;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 완료 상태와 회원 소유권을 저장 경계로 전달하는 동작 */
class DefaultHealthRecordServiceTest {

    private static final Instant ANALYZED_AT = Instant.parse("2026-09-24T01:00:00Z");

    private HealthAnalysisJobService jobService;
    private CapturingStore store;
    private DefaultHealthRecordService service;

    @BeforeEach
    void setUp() {
        jobService = mock(HealthAnalysisJobService.class);
        store = new CapturingStore();
        service = new DefaultHealthRecordService(
                jobService,
                store,
                Clock.fixed(ANALYZED_AT, ZoneOffset.UTC)
        );
    }

    /** 완료된 본인 분석만 30일 만료 기록으로 저장 */
    @Test
    void savesCompletedOwnedAnalysisForThirtyDays() {
        when(jobService.find(any(), any())).thenReturn(Optional.of(completedProgress()));

        HealthRecordService.Summary saved = service.save("42", "analysis-1");

        assertThat(store.command.userId()).isEqualTo(42L);
        assertThat(store.command.result()).isEqualTo(result());
        assertThat(store.command.expiresAt()).isEqualTo(ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60));
        assertThat(saved.id()).isEqualTo(31L);
        verify(jobService).find(
                "analysis-1",
                new HealthAnalysisJobService.Requester("42", null, null, null)
        );
    }

    /** 진행 중 작업의 저장 거절 */
    @Test
    void rejectsProcessingAnalysis() {
        when(jobService.find(any(), any())).thenReturn(Optional.of(new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.PROCESSING,
                AnalysisJobStage.GENERATING_RESULT,
                ANALYZED_AT.plusSeconds(90),
                null,
                null,
                null,
                null
        )));

        assertThatThrownBy(() -> service.save("42", "analysis-1"))
                .isInstanceOf(HealthRecordException.class)
                .hasMessage("ANALYSIS_NOT_COMPLETED");
    }

    /** 타인·만료 작업과 동일한 조회 실패 */
    @Test
    void hidesMissingOrUnauthorizedAnalysis() {
        when(jobService.find(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save("42", "analysis-1"))
                .isInstanceOf(HealthRecordException.class)
                .hasMessage("ANALYSIS_NOT_FOUND");
    }

    /** 회원 목록 Pagination 전달 */
    @Test
    void listsMemberRecordsWithPagination() {
        store.pageResult = new HealthRecordStore.PageResult(
                List.of(new HealthRecordStore.SavedRecord(
                        31L,
                        "https://news.example/article",
                        "건강 기사 제목",
                        "CAUTION",
                        ANALYZED_AT,
                        ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60)
                )),
                0,
                20,
                1,
                1,
                false
        );

        HealthRecordService.PageResult page = service.findAll("42", 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(store.listedUserId).isEqualTo(42L);
        assertThat(store.listedAt).isEqualTo(ANALYZED_AT);
    }

    /** 본인 저장 기록 개별 삭제 */
    @Test
    void deletesOwnedRecord() {
        store.deleteResult = true;

        service.delete("42", 31L);

        assertThat(store.deletedUserId).isEqualTo(42L);
        assertThat(store.deletedRecordId).isEqualTo(31L);
        assertThat(store.deletedAt).isEqualTo(ANALYZED_AT);
    }

    /** 타인 또는 없는 저장 기록 삭제의 동일 실패 */
    @Test
    void hidesMissingOrUnauthorizedRecordDelete() {
        assertThatThrownBy(() -> service.delete("42", 31L))
                .isInstanceOf(HealthRecordException.class)
                .hasMessage("HEALTH_RECORD_NOT_FOUND");
    }

    /** 회원 저장 기록 전체 삭제 */
    @Test
    void deletesAllOwnedRecords() {
        store.deleteAllResult = 2;

        assertThat(service.deleteAll("42")).isEqualTo(2);
        assertThat(store.deletedAllUserId).isEqualTo(42L);
    }

    /** 완료된 새 분석으로 기존 기록 교체와 보관 기한 재시작 */
    @Test
    void replacesRecordOnlyWithCompletedOwnedAnalysis() {
        when(jobService.find(any(), any())).thenReturn(Optional.of(completedProgress()));
        store.replaceResult = Optional.of(new HealthRecordStore.SavedRecord(
                32L,
                "https://news.example/article",
                "건강 기사 제목",
                "CAUTION",
                ANALYZED_AT,
                ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60)
        ));

        HealthRecordService.Summary replaced = service.replace("42", 31L, "analysis-1");

        assertThat(replaced.id()).isEqualTo(32L);
        assertThat(store.replaceCommand.userId()).isEqualTo(42L);
        assertThat(store.replaceCommand.recordId()).isEqualTo(31L);
        assertThat(store.replaceCommand.expiresAt())
                .isEqualTo(ANALYZED_AT.plusSeconds(30L * 24 * 60 * 60));
    }

    /** 새 분석 실패 시 기존 기록 교체 미수행 */
    @Test
    void keepsRecordWhenReanalysisIsNotCompleted() {
        when(jobService.find(any(), any())).thenReturn(Optional.of(new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.FAILED,
                AnalysisJobStage.FAILED,
                ANALYZED_AT.plusSeconds(90),
                ANALYZED_AT.plusSeconds(1800),
                null,
                null,
                new HealthAnalysisJobService.Failure(
                        "ANALYSIS_FAILED",
                        "분석 실패",
                        null
                )
        )));

        assertThatThrownBy(() -> service.replace("42", 31L, "analysis-1"))
                .isInstanceOf(HealthRecordException.class)
                .hasMessage("ANALYSIS_NOT_COMPLETED");
        assertThat(store.replaceCommand).isNull();
    }

    private HealthAnalysisJobService.Progress completedProgress() {
        return new HealthAnalysisJobService.Progress(
                "analysis-1",
                AnalysisJobStatus.COMPLETED,
                AnalysisJobStage.COMPLETED,
                ANALYZED_AT.plusSeconds(90),
                ANALYZED_AT.plusSeconds(1800),
                result(),
                null,
                null
        );
    }

    private HealthAnalysisResult result() {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"),
                        "건강 기사 제목",
                        "news.example",
                        OffsetDateTime.parse("2026-09-24T09:00:00+09:00"),
                        null
                ),
                ANALYZED_AT,
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        "건강 기사 제목",
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "근거가 부족합니다.",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                true
        );
    }

    /** 저장 명령 확인용 Port 구현 */
    private static final class CapturingStore implements HealthRecordStore {

        private SaveCommand command;
        private long listedUserId;
        private Instant listedAt;
        private PageResult pageResult;
        private boolean deleteResult;
        private int deleteAllResult;
        private Optional<SavedRecord> replaceResult = Optional.empty();
        private long deletedUserId;
        private long deletedRecordId;
        private long deletedAllUserId;
        private Instant deletedAt;
        private ReplaceCommand replaceCommand;

        @Override
        public SavedRecord save(SaveCommand command) {
            this.command = command;
            return new SavedRecord(
                    31L,
                    command.result().article().url().toASCIIString(),
                    command.result().article().title(),
                    command.result().overallStatus().name(),
                    command.result().analyzedAt(),
                    command.expiresAt()
            );
        }

        @Override
        public PageResult findAll(long userId, Instant activeAt, int page, int size) {
            listedUserId = userId;
            listedAt = activeAt;
            return pageResult;
        }

        @Override
        public int deleteExpiredAtOrBefore(Instant cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean delete(long userId, long recordId, Instant activeAt) {
            deletedUserId = userId;
            deletedRecordId = recordId;
            deletedAt = activeAt;
            return deleteResult;
        }

        @Override
        public int deleteAll(long userId) {
            deletedAllUserId = userId;
            return deleteAllResult;
        }

        @Override
        public Optional<SavedRecord> replace(ReplaceCommand command, Instant activeAt) {
            replaceCommand = command;
            return replaceResult;
        }
    }
}
