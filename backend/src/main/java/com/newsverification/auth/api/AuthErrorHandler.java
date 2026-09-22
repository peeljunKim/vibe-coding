/* 일반 로그인 API 오류 응답 변환 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.LoginException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 계정 존재 여부를 숨기는 로그인 ProblemDetail */
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthErrorHandler {

    @ExceptionHandler(LoginException.class)
    public ResponseEntity<ProblemDetail> handleLoginFailure(LoginException exception) {
        if ("LOGIN_LOCKED".equals(exception.code())) {
            return problem(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "로그인이 일시 제한되었습니다. 30분 후 이용 가능합니다.",
                    exception.code(),
                    exception.retryAfterSeconds()
            );
        }
        return problem(
                HttpStatus.UNAUTHORIZED,
                "아이디 또는 비밀번호를 확인해 주세요.",
                "INVALID_CREDENTIALS",
                0
        );
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest() {
        return problem(
                HttpStatus.BAD_REQUEST,
                "아이디와 비밀번호를 확인해 주세요.",
                "INVALID_LOGIN_REQUEST",
                0
        );
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String detail,
            String code,
            long retryAfterSeconds
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Login failed");
        problem.setProperty("code", code);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfterSeconds > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return response.body(problem);
    }
}
