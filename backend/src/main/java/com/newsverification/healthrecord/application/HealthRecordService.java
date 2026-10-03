/* 저장 건강 분석 Use Case Port */
package com.newsverification.healthrecord.application;

import java.time.Instant;
import java.util.List;

/** 회원의 건강 분석 저장 기록 관리 경계 */
public interface HealthRecordService {

    /** 완료된 회원 분석 저장 */
    Summary save(String memberId, String analysisId);

    /** 회원 저장 기록 페이지 조회 */
    PageResult findAll(String memberId, int page, int size);

    /** 회원 저장 기록 개별 삭제 */
    void delete(String memberId, long recordId);

    /** 회원 저장 기록 전체 삭제 */
    int deleteAll(String memberId);

    /** 완료된 새 분석으로 기존 저장 기록 교체 */
    Summary replace(String memberId, long recordId, String analysisId);

    /** 저장 기록 목록 항목 */
    record Summary(
            long id,
            String articleUrl,
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
