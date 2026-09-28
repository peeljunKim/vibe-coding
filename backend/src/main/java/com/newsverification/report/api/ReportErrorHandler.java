/* 문제 신고 오류 응답 */
package com.newsverification.report.api;

import com.newsverification.report.application.ReportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 내부 원인과 소유권 정보를 제한한 신고 ProblemDetail */
@RestControllerAdvice(assignableTypes = {ReportController.class, AdminReportController.class})
public class ReportErrorHandler {

    /** 신고 업무 오류의 공개 응답 변환 */
    @ExceptionHandler(ReportException.class)
    public ResponseEntity<ProblemDetail> handle(ReportException exception) {
        return switch (exception.getMessage()) {
            case "ANALYSIS_NOT_FOUND", "REPORT_NOT_FOUND" -> problem(
                    HttpStatus.NOT_FOUND,
                    "Report not found",
                    "요청한 신고 대상을 찾을 수 없습니다.",
                    exception.getMessage()
            );
            case "ANALYSIS_NOT_COMPLETED", "REPORT_STATE_CONFLICT", "VERSION_CONFLICT" -> problem(
                    HttpStatus.CONFLICT,
                    "Report conflict",
                    "현재 상태에서는 요청을 처리할 수 없습니다.",
                    exception.getMessage()
            );
            case "REPORT_ACCESS_DENIED" -> problem(
                    HttpStatus.FORBIDDEN,
                    "Report access denied",
                    "신고에 접근할 수 없습니다.",
                    "REPORT_ACCESS_DENIED"
            );
            default -> invalidRequest(exception.getMessage());
        };
    }

    /** 요청 형식 오류 변환 */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> invalidInput() {
        return invalidRequest("INVALID_REQUEST");
    }

    private ResponseEntity<ProblemDetail> invalidRequest(String code) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "요청 형식을 확인해 주세요.",
                code
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String title,
            String detail,
            String code
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
