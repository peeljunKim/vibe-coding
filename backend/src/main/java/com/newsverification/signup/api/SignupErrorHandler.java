/* 회원가입 API 오류 응답 변환 */
package com.newsverification.signup.api;

import com.newsverification.signup.application.SignupException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Set;

/** 내부 원인을 제한한 회원가입 ProblemDetail */
@RestControllerAdvice(assignableTypes = SignupController.class)
public class SignupErrorHandler {

    private static final Set<String> CONFLICT_CODES = Set.of("DUPLICATE_ACCOUNT");
    private static final Set<String> RATE_LIMIT_CODES = Set.of(
            "VERIFICATION_BLOCKED",
            "RESEND_TOO_SOON",
            "DAILY_SEND_LIMIT_EXCEEDED"
    );
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("INVALID_INVITE_CODE", "초대 코드를 확인해 주세요."),
            Map.entry("PASSWORD_CONFIRMATION_MISMATCH", "비밀번호 확인 값이 일치하지 않습니다."),
            Map.entry("AGREEMENTS_REQUIRED", "개인정보 처리와 서비스 이용약관 동의가 필요합니다."),
            Map.entry("DUPLICATE_ACCOUNT", "이미 사용 중인 계정 정보가 있습니다."),
            Map.entry("SIGNUP_NOT_FOUND", "회원가입 정보를 확인하지 못했습니다."),
            Map.entry("INVALID_VERIFICATION_CODE", "인증번호를 확인해 주세요."),
            Map.entry("VERIFICATION_EXPIRED", "인증번호가 만료되었습니다. 다시 발급해 주세요."),
            Map.entry("VERIFICATION_BLOCKED", "인증이 일시 제한되었습니다. 30분 후 다시 시도해 주세요."),
            Map.entry("RESEND_TOO_SOON", "인증번호는 1분 후 다시 발급할 수 있습니다."),
            Map.entry("DAILY_SEND_LIMIT_EXCEEDED", "오늘 가능한 인증번호 발송 횟수를 모두 사용했습니다.")
    );

    @ExceptionHandler(SignupException.class)
    public ResponseEntity<ProblemDetail> handleSignupFailure(SignupException exception) {
        HttpStatus status = statusFor(exception.code());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                MESSAGES.getOrDefault(exception.code(), "회원가입을 완료하지 못했습니다.")
        );
        problem.setProperty("code", exception.code());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "입력 내용을 확인해 주세요."
        );
        problem.setProperty("code", "INVALID_SIGNUP_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    private HttpStatus statusFor(String code) {
        if (CONFLICT_CODES.contains(code)) {
            return HttpStatus.CONFLICT;
        }
        if (RATE_LIMIT_CODES.contains(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if ("SIGNUP_NOT_FOUND".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("VERIFICATION_EXPIRED".equals(code)) {
            return HttpStatus.GONE;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
