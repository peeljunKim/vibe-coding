/* 만료 제목 공유 정리 */
package com.newsverification.share.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** 만료·해제 제목 Snapshot 일일 삭제 */
@Service
public class ExpiredHeadlineShareCleanupService {

    private final ShareStore store;
    private final Clock clock;

    public ExpiredHeadlineShareCleanupService(ShareStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(cron = "0 25 3 * * *", zone = "Asia/Seoul")
    public int cleanup() {
        return store.deleteExpiredHeadline(clock.instant());
    }
}
