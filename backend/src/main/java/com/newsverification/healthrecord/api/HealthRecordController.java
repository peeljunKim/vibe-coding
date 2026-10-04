/* 저장 건강 분석 API */
package com.newsverification.healthrecord.api;

import com.newsverification.healthrecord.application.HealthRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 회원의 건강 분석 저장 기록 HTTP Adapter */
@RestController
@RequestMapping("/api/health-records")
public class HealthRecordController {

    private final HealthRecordService service;

    public HealthRecordController(HealthRecordService service) {
        this.service = service;
    }

    /** 완료된 분석의 명시적 저장 */
    @PostMapping
    public ResponseEntity<HealthRecordService.Summary> save(
            @Valid @RequestBody SaveRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.save(authentication.getName(), request.analysisId()));
    }

    /** 최신 분석일부터 정렬된 저장 기록 조회 */
    @GetMapping
    public ResponseEntity<HealthRecordService.PageResult> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.findAll(authentication.getName(), page, size));
    }

    /** 본인 저장 기록 개별 삭제 */
    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(
            @PathVariable long recordId,
            Authentication authentication
    ) {
        service.delete(authentication.getName(), recordId);
        return ResponseEntity.noContent().build();
    }

    /** 본인 저장 기록 전체 삭제 */
    @DeleteMapping
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        service.deleteAll(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** 완료된 새 분석으로 기존 저장 기록 교체 */
    @PutMapping("/{recordId}")
    public ResponseEntity<HealthRecordService.Summary> replace(
            @PathVariable long recordId,
            @Valid @RequestBody ReplaceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.replace(authentication.getName(), recordId, request.analysisId()));
    }

    /** 저장할 분석 작업 식별자 */
    public record SaveRequest(@NotBlank String analysisId) {
    }

    /** 교체할 분석 작업 식별자 */
    public record ReplaceRequest(@NotBlank String analysisId) {
    }
}
