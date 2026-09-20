/* 건강 분석 비동기 HTTP Adapter용 Application Port */
package com.newsverification.health.application;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Worker 연결 전에도 HTTP 계약을 분리하는 분석 작업 경계 */
public interface HealthAnalysisJobService {

    /** 건강 분석 작업 접수 */
    Acceptance accept(String articleUrl, Requester requester);

    /** 소유권을 포함한 건강 분석 작업 조회 */
    Optional<Progress> find(String analysisId, Requester requester);

    /** 인증 회원 또는 비회원 조회 자격 */
    record Requester(
            String memberId,
            String guestBrowserId,
            String guestAccessToken,
            String clientIp
    ) {

        /** IP가 필요 없는 기존 조회 호환 구성 */
        public Requester(String memberId, String guestBrowserId, String guestAccessToken) {
            this(memberId, guestBrowserId, guestAccessToken, null);
        }
    }

    /** 분석 이용량 응답 값 */
    record Usage(
            int limit,
            int used,
            int remaining,
            boolean charged
    ) {
    }

    /** 비회원 브라우저 Cookie 발급 값 */
    record GuestBrowserCookie(
            String value,
            Duration maxAge,
            boolean secure
    ) {
    }

    /** 분석 작업 접수 결과 */
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

    /** 진행 중 분석 작업 조회 결과 */
    record Progress(
            String analysisId,
            AnalysisJobStatus status,
            AnalysisJobStage stage,
            Instant deadlineAt,
            Instant expiresAt,
            HealthAnalysisResult result,
            Usage usage,
            Failure error
    ) {
    }

    /** 비동기 분석 실패 응답 값 */
    record Failure(
            String code,
            String detail,
            Usage usage
    ) {
    }
}
