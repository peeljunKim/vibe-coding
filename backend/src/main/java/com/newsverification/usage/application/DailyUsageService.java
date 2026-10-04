/* 기능별 일일 이용량 조회 경계 */
package com.newsverification.usage.application;

import java.time.Duration;
import java.time.Instant;

/** 회원·비회원의 현재 분석 이용량 조회 경계 */
public interface DailyUsageService {

    /** 현재 이용량과 초기화 시각 조회 */
    Snapshot get(Requester requester);

    /** 인증 회원 또는 비회원 조회 자격 */
    record Requester(String memberId, String guestBrowserId, String clientIp) {
    }

    /** 기능별 이용 횟수 */
    record Counter(int limit, int used, int remaining) {
    }

    /** 비회원 브라우저 Cookie 발급 값 */
    record GuestBrowserCookie(String value, Duration maxAge, boolean secure) {
    }

    /** 일일 이용량 조회 결과 */
    record Snapshot(
            String timezone,
            Instant resetsAt,
            Counter health,
            Counter headline,
            GuestBrowserCookie guestBrowserCookie
    ) {
    }
}
