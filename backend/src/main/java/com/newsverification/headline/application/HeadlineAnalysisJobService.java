/* 기사 제목 분석 비동기 HTTP Adapter용 Application Port */
package com.newsverification.headline.application;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** 제목 분석 접수와 소유권 Polling 경계 */
public interface HeadlineAnalysisJobService {

    /** 기사 제목 분석 작업 접수 */
    Acceptance accept(String articleUrl, Requester requester);

    /** 소유권을 포함한 기사 제목 분석 작업 조회 */
    Optional<Progress> find(String analysisId, Requester requester);

    /** 인증 회원 또는 비회원 조회 자격 */
    record Requester(
            String memberId,
            String guestBrowserId,
            String guestAccessToken,
            String clientIp
    ) {
    }

    /** 제목 분석 이용량 응답 값 */
    record Usage(int limit, int used, int remaining, boolean charged) {
    }

    /** 비회원 브라우저 Cookie 발급 값 */
    record GuestBrowserCookie(String value, Duration maxAge, boolean secure) {
    }

    /** 제목 분석 작업 접수 결과 */
    record Acceptance(
            String analysisId,
            AnalysisJobStatus status,
            AnalysisJobStage stage,
            Usage usage,
            Instant acceptedAt,
            Instant deadlineAt,
            String guestAccessToken,
            GuestBrowserCookie guestBrowserCookie
    ) {
    }

    /** 제목 분석 작업 조회 결과 */
    record Progress(
            String analysisId,
            AnalysisJobStatus status,
            AnalysisJobStage stage,
            Instant deadlineAt,
            Instant expiresAt,
            HeadlineAnalysisResult result,
            Usage usage,
            Failure error
    ) {
    }

    /** 비동기 제목 분석 실패 값 */
    record Failure(String code, String detail, Usage usage) {
    }
}
