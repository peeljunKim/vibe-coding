/* 기사 제목 분석 Queue Port */
package com.newsverification.headline.application;

import java.time.Duration;
import java.util.Optional;

/** Redis Streams 교체 가능한 제목 분석 Queue 경계 */
public interface HeadlineAnalysisQueue {

    /** Queue 수용 시 작업 추가 */
    boolean enqueue(HeadlineAnalysisTask task);

    /** 자동 재전달 없는 단일 작업 소비 */
    Optional<HeadlineAnalysisTask> take();

    /** 제목 분석 단일 Worker Lease 획득 */
    boolean tryAcquireWorker(String ownerToken, Duration leaseTime);

    /** 소유자 일치 시 Worker Lease 해제 */
    void releaseWorker(String ownerToken);
}
