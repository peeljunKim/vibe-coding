/* 회원 문제 신고 API */
package com.newsverification.report.api;

import com.newsverification.report.application.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 로그인 회원의 신고 접수와 본인 신고 조회 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** 소유한 완료 분석 결과 신고 */
    @PostMapping
    public ResponseEntity<ReportService.Detail> create(
            @Valid @RequestBody CreateRequest request,
            Authentication authentication
    ) {
        ReportService.Detail created = service.create(authentication.getName(), new ReportService.CreateCommand(
                request.analysisType(), request.analysisId(), request.reportType(), request.description()
        ));
        return ResponseEntity.created(URI.create("/api/reports/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    /** 본인 신고 목록 조회 */
    @GetMapping
    public ResponseEntity<ReportService.PageResult> findMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findMine(authentication.getName(), page, size));
    }

    /** 본인 신고 상세 조회 */
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportService.Detail> findOne(
            @PathVariable long reportId,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findOne(authentication.getName(), reportId));
    }

    /** 신고 생성 입력 */
    public record CreateRequest(
            @NotBlank String analysisType,
            @NotBlank String analysisId,
            @NotBlank String reportType,
            @NotBlank String description
    ) {
    }
}
