/* 분석 결과 공유 API */
package com.newsverification.share.api;

import com.newsverification.share.application.ShareService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 회원 공유 관리와 비로그인 읽기 전용 조회 */
@Validated
@RestController
public class ShareController {

    private static final String SHARE_TOKEN_HEADER = "X-Share-Token";
    private final ShareService service;

    public ShareController(ShareService service) {
        this.service = service;
    }

    @PostMapping("/api/health-records/{recordId}/shares")
    public ResponseEntity<ShareService.Created> createHealth(
            @PathVariable long recordId,
            Authentication authentication
    ) {
        return privateResponse(service.createHealth(authentication.getName(), recordId));
    }

    @PostMapping("/api/analyses/headline/{analysisId}/shares")
    public ResponseEntity<ShareService.Created> createHeadline(
            @PathVariable String analysisId,
            Authentication authentication
    ) {
        return privateResponse(service.createHeadline(authentication.getName(), analysisId));
    }

    @GetMapping("/api/shares/health")
    public ResponseEntity<ShareService.HealthResult> findHealth(
            @RequestHeader(SHARE_TOKEN_HEADER) @NotBlank String shareToken
    ) {
        return publicResponse(service.findHealth(shareToken));
    }

    @GetMapping("/api/shares/headline")
    public ResponseEntity<ShareService.HeadlineResult> findHeadline(
            @RequestHeader(SHARE_TOKEN_HEADER) @NotBlank String shareToken
    ) {
        return publicResponse(service.findHeadline(shareToken));
    }

    @DeleteMapping("/api/shares/health")
    public ResponseEntity<Void> revokeHealth(
            @RequestHeader(SHARE_TOKEN_HEADER) @NotBlank String shareToken,
            Authentication authentication
    ) {
        service.revokeHealth(authentication.getName(), shareToken);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/api/shares/headline")
    public ResponseEntity<Void> revokeHeadline(
            @RequestHeader(SHARE_TOKEN_HEADER) @NotBlank String shareToken,
            Authentication authentication
    ) {
        service.revokeHeadline(authentication.getName(), shareToken);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> publicResponse(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, SHARE_TOKEN_HEADER)
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(body);
    }
}
