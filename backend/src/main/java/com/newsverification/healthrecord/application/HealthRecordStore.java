/* 저장 건강 분석 Persistence Port */
package com.newsverification.healthrecord.application;

import com.newsverification.health.application.HealthAnalysisResult;

import java.time.Instant;
import java.util.List;

/** 건강 분석 결과와 목록의 영속 경계 */
public interface HealthRecordStore {

    /** 같은 분석 결과의 중복 생성을 막는 저장 */
    SavedRecord save(SaveCommand command);

    /** 회원별 최신 기록 페이지 조회 */
    PageResult findAll(long userId, Instant activeAt, int page, int size);

    /** 기준 시각 이하 만료 기록 삭제 */
    int deleteExpiredAtOrBefore(Instant cutoff);

    /** 건강 분석 저장 입력 */
    record SaveCommand(long userId, HealthAnalysisResult result, Instant expiresAt) {
    }

    /** 저장 기록 요약 */
    record SavedRecord(
            long id,
            String title,
            String overallStatus,
            Instant analyzedAt,
            Instant expiresAt
    ) {
    }

    /** Persistence 목록 페이지 */
    record PageResult(
            List<SavedRecord> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public PageResult {
            items = List.copyOf(items);
        }
    }
}
