/* 공유 API 공개 오류 변환 */
package com.newsverification.share.api;

import com.newsverification.share.application.ShareException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Token·소유권·내부 원인을 제한한 오류 응답 */
@RestControllerAdvice(assignableTypes = ShareController.class)
public class ShareErrorHandler {

    @ExceptionHandler(ShareException.class)
    public ResponseEntity<ProblemDetail> handle(ShareException exception) {
        if ("SHARE_ACCESS_DENIED".equals(exception.getMessage())) {
            return problem(HttpStatus.FORBIDDEN, "Share access denied", "공유 권한을 확인해 주세요.",
                    "SHARE_ACCESS_DENIED");
        }
        return notFound();
    }

    @ExceptionHandler({MissingRequestHeaderException.class, ConstraintViolationException.class})
    public ResponseEntity<ProblemDetail> invalidToken() {
        return notFound();
    }

    private ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Share not found", "공유 결과를 확인할 수 없습니다.",
                "SHARE_NOT_FOUND");
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
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(problem);
    }
}
