/* 저장 건강 분석 Use Case 구현 */
package com.newsverification.healthrecord.application;

import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.health.application.HealthAnalysisJobService;
import com.newsverification.health.application.HealthAnalysisResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/** 완료 작업 소유권과 영속 저장을 연결하는 Application Service */
@Service
public class DefaultHealthRecordService implements HealthRecordService {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final HealthAnalysisJobService jobService;
    private final HealthRecordStore store;
    private final Clock clock;

    public DefaultHealthRecordService(
            HealthAnalysisJobService jobService,
            HealthRecordStore store,
            Clock clock
    ) {
        this.jobService = jobService;
        this.store = store;
        this.clock = clock;
    }

    /** 완료된 본인 분석의 30일 저장 */
    @Override
    public Summary save(String memberId, String analysisId) {
        long userId = userId(memberId);
        HealthAnalysisJobService.Progress progress = jobService.find(
                        analysisId,
                        new HealthAnalysisJobService.Requester(memberId, null, null, null)
                )
                .orElseThrow(() -> new HealthRecordException("ANALYSIS_NOT_FOUND"));
        HealthAnalysisResult result = progress.result();
        if (progress.status() != AnalysisJobStatus.COMPLETED || result == null) {
            throw new HealthRecordException("ANALYSIS_NOT_COMPLETED");
        }

        return toSummary(store.save(new HealthRecordStore.SaveCommand(
                userId,
                result,
                result.analyzedAt().plus(RETENTION)
        )));
    }

    /** 회원별 저장 기록 조회 */
    @Override
    public PageResult findAll(String memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new HealthRecordException("INVALID_PAGINATION");
        }
        HealthRecordStore.PageResult result = store.findAll(
                userId(memberId),
                clock.instant(),
                page,
                size
        );
        return new PageResult(
                result.items().stream().map(DefaultHealthRecordService::toSummary).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasNext()
        );
    }

    private long userId(String memberId) {
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException exception) {
            throw new HealthRecordException("INVALID_MEMBER_ID");
        }
    }

    private static Summary toSummary(HealthRecordStore.SavedRecord record) {
        return new Summary(
                record.id(),
                record.title(),
                record.overallStatus(),
                record.analyzedAt(),
                record.expiresAt()
        );
    }
}
