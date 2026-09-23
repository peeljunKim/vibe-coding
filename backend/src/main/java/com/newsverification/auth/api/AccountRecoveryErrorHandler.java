/* 계정 복구 API 오류 응답 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.AccountRecoveryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Set;

/** 내부 계정 정보를 숨기는 계정 복구 ProblemDetail */
@RestControllerAdvice(assignableTypes = AccountRecoveryController.class)
public class AccountRecoveryErrorHandler {

    private static final Set<String> RATE_LIMIT_CODES = Set.of(
            "VERIFICATION_BLOCKED",
            "RESEND_TOO_SOON",
            "DAILY_SEND_LIMIT_EXCEEDED"
    );
    private static final Map<String, String> MESSAGES = Map.of(
            "INVALID_VERIFICATION_CODE", "인증번호를 확인해 주세요.",
            "VERIFICATION_EXPIRED", "인증번호가 만료되었습니다. 다시 발급해 주세요.",
            "VERIFICATION_BLOCKED", "인증이 일시 제한되었습니다. 30분 후 다시 시도해 주세요.",
            "RESEND_TOO_SOON", "인증번호는 1분 후 다시 발급할 수 있습니다.",
            "DAILY_SEND_LIMIT_EXCEEDED", "오늘 가능한 인증번호 발송 횟수를 모두 사용했습니다.",
            "PASSWORD_CONFIRMATION_MISMATCH", "비밀번호 확인 값이 일치하지 않습니다."
    );

    @ExceptionHandler(AccountRecoveryException.class)
    public ResponseEntity<ProblemDetail> handleRecoveryFailure(AccountRecoveryException exception) {
        HttpStatus status = statusFor(exception.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                MESSAGES.getOrDefault(exception.code(), "계정 복구를 완료하지 못했습니다.")
        );
        problem.setTitle("Account recovery failed");
        problem.setProperty("code", exception.code());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "입력 내용을 확인해 주세요."
        );
        problem.setTitle("Invalid account recovery request");
        problem.setProperty("code", "INVALID_RECOVERY_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    private HttpStatus statusFor(String code) {
        if (RATE_LIMIT_CODES.contains(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if ("VERIFICATION_EXPIRED".equals(code)) {
            return HttpStatus.GONE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
