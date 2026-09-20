/* 기사 제목 분석 비동기 접수와 상태 조회 API */
package com.newsverification.headline.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import com.newsverification.headline.application.HeadlineAnalysisServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

/** 제목 분석 Application Port를 노출하는 HTTP Adapter */
@RestController
@RequestMapping("/api/analyses/headline")
public class HeadlineAnalysisJobController {

    private static final int POLL_AFTER_SECONDS = 2;
    private static final String GUEST_COOKIE_NAME = "NEWS_VERIFICATION_GUEST";
    private static final String GUEST_ACCESS_HEADER = "X-Analysis-Access-Token";

    private final Optional<HeadlineAnalysisJobService> jobService;

    /** Worker 연결 전 Optional Port 구성 */
    public HeadlineAnalysisJobController(Optional<HeadlineAnalysisJobService> jobService) {
        this.jobService = jobService;
    }

    /** 기사 제목 분석 작업 접수 */
    @PostMapping
    public ResponseEntity<?> accept(
            @Valid @RequestBody AnalysisRequest request,
            @CookieValue(name = GUEST_COOKIE_NAME, required = false) String guestBrowserId,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        if (jobService.isEmpty()) {
            return serviceUnavailable();
        }
        HeadlineAnalysisJobService.Acceptance acceptance = jobService.get().accept(
                request.articleUrl(),
                requester(authentication, guestBrowserId, null, servletRequest.getRemoteAddr())
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.accepted()
                .location(URI.create("/api/analyses/headline/" + acceptance.analysisId()))
                .header(HttpHeaders.RETRY_AFTER, Integer.toString(POLL_AFTER_SECONDS))
                .cacheControl(CacheControl.noStore());
        if (acceptance.guestBrowserCookie() != null) {
            var cookie = acceptance.guestBrowserCookie();
            builder.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(GUEST_COOKIE_NAME, cookie.value())
                    .httpOnly(true).secure(cookie.secure()).sameSite("Lax").path("/")
                    .maxAge(cookie.maxAge()).build().toString());
        }
        return builder.body(AcceptedResponse.from(acceptance));
    }

    /** 기사 제목 분석 상태 Polling */
    @GetMapping("/{analysisId}")
    public ResponseEntity<?> getStatus(
            @PathVariable String analysisId,
            @CookieValue(name = GUEST_COOKIE_NAME, required = false) String guestBrowserId,
            @RequestHeader(name = GUEST_ACCESS_HEADER, required = false) String guestAccessToken,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        if (jobService.isEmpty()) {
            return serviceUnavailable();
        }
        return jobService.get()
                .find(analysisId, requester(
                        authentication, guestBrowserId, guestAccessToken, servletRequest.getRemoteAddr()
                ))
                .<ResponseEntity<?>>map(progress -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .body(ProgressResponse.from(progress)))
                .orElseGet(this::notFound);
    }

    /** 인증과 비회원 자격의 Application 요청 변환 */
    private HeadlineAnalysisJobService.Requester requester(
            Authentication authentication,
            String guestBrowserId,
            String guestAccessToken,
            String clientIp
    ) {
        String memberId = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            memberId = authentication.getName();
        }
        return new HeadlineAnalysisJobService.Requester(
                memberId, guestBrowserId, guestAccessToken, clientIp
        );
    }

    /** 노출하지 않는 작업 조회 실패 응답 */
    private ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Analysis job not found",
                "요청한 분석 작업을 찾을 수 없습니다.", "ANALYSIS_NOT_FOUND");
    }

    /** 제목 분석 서비스 장애 응답 */
    private ResponseEntity<ProblemDetail> serviceUnavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Analysis service unavailable",
                "분석 서비스를 현재 사용할 수 없습니다.", "ANALYSIS_SERVICE_UNAVAILABLE");
    }

    /** 요청 원문을 노출하지 않는 입력 오류 응답 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "요청 형식을 확인해 주세요.", "INVALID_REQUEST");
    }

    /** Queue 포화와 Redis 장애 응답 */
    @ExceptionHandler(HeadlineAnalysisServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> unavailableAnalysisService() {
        return serviceUnavailable();
    }

    /** 공통 ProblemDetail 응답 생성 */
    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    /** 분석 대상 URL 요청 */
    public record AnalysisRequest(@NotBlank String articleUrl) {
    }

    /** 제목 분석 이용량 응답 */
    public record UsageResponse(int limit, int used, int remaining, boolean charged) {
        private static UsageResponse from(HeadlineAnalysisJobService.Usage usage) {
            return new UsageResponse(usage.limit(), usage.used(), usage.remaining(), usage.charged());
        }
    }

    /** 제목 분석 접수 응답 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AcceptedResponse(
            String analysisId,
            String status,
            String stage,
            UsageResponse usage,
            Instant acceptedAt,
            Instant deadlineAt,
            int pollAfterSeconds,
            String guestAccessToken
    ) {
        private static AcceptedResponse from(HeadlineAnalysisJobService.Acceptance acceptance) {
            return new AcceptedResponse(
                    acceptance.analysisId(), acceptance.status().name(), acceptance.stage().name(),
                    UsageResponse.from(acceptance.usage()), acceptance.acceptedAt(), acceptance.deadlineAt(),
                    POLL_AFTER_SECONDS, acceptance.guestAccessToken()
            );
        }
    }

    /** 제목 분석 진행과 종료 응답 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProgressResponse(
            String analysisId,
            String status,
            String stage,
            Instant deadlineAt,
            Instant expiresAt,
            HeadlineAnalysisResult result,
            UsageResponse usage,
            FailureResponse error,
            Integer pollAfterSeconds
    ) {
        private static ProgressResponse from(HeadlineAnalysisJobService.Progress progress) {
            boolean processing = progress.status() == AnalysisJobStatus.PROCESSING;
            return new ProgressResponse(
                    progress.analysisId(), progress.status().name(), progress.stage().name(),
                    processing ? progress.deadlineAt() : null, progress.expiresAt(), progress.result(),
                    progress.usage() == null ? null : UsageResponse.from(progress.usage()),
                    progress.error() == null ? null : FailureResponse.from(progress.error()),
                    processing ? POLL_AFTER_SECONDS : null
            );
        }
    }

    /** 제목 분석 실패 응답 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FailureResponse(String code, String detail, UsageResponse usage) {
        private static FailureResponse from(HeadlineAnalysisJobService.Failure failure) {
            return new FailureResponse(
                    failure.code(), failure.detail(),
                    failure.usage() == null ? null : UsageResponse.from(failure.usage())
            );
        }
    }
}
