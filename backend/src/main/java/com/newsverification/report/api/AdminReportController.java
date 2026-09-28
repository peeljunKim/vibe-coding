/* 관리자 문제 신고 API */
package com.newsverification.report.api;

import com.newsverification.report.application.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN 권한의 신고 조회와 처리 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService service;

    public AdminReportController(ReportService service) {
        this.service = service;
    }

    /** 전체 신고 목록 조회 */
    @GetMapping
    public ResponseEntity<ReportService.PageResult> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findAllAsAdmin(page, size));
    }

    /** 신고 상세와 불변 Snapshot 조회 */
    @GetMapping("/{reportId}")
    public ResponseEntity<ReportService.Detail> findOne(@PathVariable long reportId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findOneAsAdmin(reportId));
    }

    /** 신고 상태와 짧은 답변 변경 */
    @PatchMapping("/{reportId}")
    public ResponseEntity<ReportService.Detail> update(
            @PathVariable long reportId,
            @Valid @RequestBody UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.updateAsAdmin(authentication.getName(), reportId, new ReportService.UpdateCommand(
                        request.status(), request.adminReply(), request.version()
                )));
    }

    /** 관리자 처리 입력 */
    public record UpdateRequest(
            @NotBlank String status,
            String adminReply,
            @PositiveOrZero long version
    ) {
    }
}
