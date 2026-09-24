/* 저장 건강 분석 오류 응답 */
package com.newsverification.healthrecord.api;

import com.newsverification.healthrecord.application.HealthRecordException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 저장·목록 오류의 공개 ProblemDetail 변환 */
@RestControllerAdvice(assignableTypes = HealthRecordController.class)
public class HealthRecordErrorHandler {

    /** 업무 조건 오류 변환 */
    @ExceptionHandler(HealthRecordException.class)
    public ResponseEntity<ProblemDetail> handle(HealthRecordException exception) {
        return switch (exception.getMessage()) {
            case "ANALYSIS_NOT_FOUND" -> problem(
                    HttpStatus.NOT_FOUND,
                    "Analysis job not found",
                    "요청한 분석 작업을 찾을 수 없습니다.",
                    "ANALYSIS_NOT_FOUND"
            );
            case "ANALYSIS_NOT_COMPLETED" -> problem(
                    HttpStatus.CONFLICT,
                    "Analysis not completed",
                    "완료된 건강 분석 결과만 저장할 수 있습니다.",
                    "ANALYSIS_NOT_COMPLETED"
            );
            case "INVALID_PAGINATION" -> invalidPagination();
            default -> problem(
                    HttpStatus.FORBIDDEN,
                    "Health record access denied",
                    "저장 기록에 접근할 수 없습니다.",
                    "HEALTH_RECORD_ACCESS_DENIED"
            );
        };
    }

    /** 요청 형식 오류 변환 */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ProblemDetail> invalidInput() {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "요청 형식을 확인해 주세요.",
                "INVALID_REQUEST"
        );
    }

    private ResponseEntity<ProblemDetail> invalidPagination() {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "요청 형식을 확인해 주세요.",
                "INVALID_PAGINATION"
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
