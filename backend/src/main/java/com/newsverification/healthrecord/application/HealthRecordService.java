/* 저장 건강 분석 Use Case Port */
package com.newsverification.healthrecord.application;

import java.time.Instant;
import java.util.List;

/** 회원의 명시적 건강 분석 저장과 목록 조회 경계 */
public interface HealthRecordService {

    /** 완료된 회원 분석 저장 */
    Summary save(String memberId, String analysisId);

    /** 회원 저장 기록 페이지 조회 */
    PageResult findAll(String memberId, int page, int size);

    /** 저장 기록 목록 항목 */
    record Summary(
            long id,
            String title,
            String overallStatus,
            Instant analyzedAt,
            Instant expiresAt
    ) {
    }

    /** 저장 기록 페이지 */
    record PageResult(
            List<Summary> items,
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
