/* 기능별 일일 이용량 HTTP API */
package com.newsverification.usage.api;

import com.newsverification.usage.application.DailyUsageService;
import com.newsverification.usage.application.DailyUsageServiceUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 홈 화면용 건강·제목 일일 이용량 조회 */
@RestController
@RequestMapping("/api/usage")
public class DailyUsageController {

    private static final String GUEST_COOKIE_NAME = "NEWS_VERIFICATION_GUEST";

    private final DailyUsageService dailyUsageService;

    /** 일일 이용량 Application Port 구성 */
    public DailyUsageController(DailyUsageService dailyUsageService) {
        this.dailyUsageService = dailyUsageService;
    }

    /** 회원·비회원 현재 이용량 공개 조회 */
    @GetMapping
    public ResponseEntity<DailyUsageResponse> get(
            @CookieValue(name = GUEST_COOKIE_NAME, required = false) String guestBrowserId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        DailyUsageService.Snapshot snapshot = dailyUsageService.get(new DailyUsageService.Requester(
                memberId(authentication), guestBrowserId, request.getRemoteAddr()
        ));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (snapshot.guestBrowserCookie() != null) {
            var cookie = snapshot.guestBrowserCookie();
            response.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(GUEST_COOKIE_NAME, cookie.value())
                    .httpOnly(true)
                    .secure(cookie.secure())
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(cookie.maxAge())
                    .build()
                    .toString());
        }
        return response.body(DailyUsageResponse.from(snapshot));
    }

    /** Redis 이용량 조회 장애 응답 */
    @ExceptionHandler(DailyUsageServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> unavailableUsageService() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "이용 횟수를 현재 확인할 수 없습니다."
        );
        problem.setTitle("Usage service unavailable");
        problem.setProperty("code", "USAGE_SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** 인증된 회원 식별자 확인 */
    private String memberId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    /** 기능별 이용량 응답 */
    public record UsageCounterResponse(int limit, int used, int remaining) {

        /** Application 이용량 변환 */
        private static UsageCounterResponse from(DailyUsageService.Counter counter) {
            return new UsageCounterResponse(counter.limit(), counter.used(), counter.remaining());
        }
    }

    /** 일일 이용량 응답 */
    public record DailyUsageResponse(
            String timezone,
            Instant resetsAt,
            UsageCounterResponse health,
            UsageCounterResponse headline
    ) {

        /** Application 조회 결과 변환 */
        private static DailyUsageResponse from(DailyUsageService.Snapshot snapshot) {
            return new DailyUsageResponse(
                    snapshot.timezone(),
                    snapshot.resetsAt(),
                    UsageCounterResponse.from(snapshot.health()),
                    UsageCounterResponse.from(snapshot.headline())
            );
        }
    }
}
