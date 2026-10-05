/* 소셜 가입 오류 응답 변환 */
package com.newsverification.auth.api;

import com.newsverification.auth.application.SocialLoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Provider 신원과 내부 충돌 정보를 제한한 ProblemDetail */
@RestControllerAdvice(assignableTypes = SocialSignupController.class)
public class SocialSignupErrorHandler {

    private static final Map<String, String> MESSAGES = Map.of(
            "INVALID_INVITE_CODE", "초대 코드를 확인해 주세요.",
            "AGREEMENTS_REQUIRED", "개인정보 처리와 서비스 이용약관 동의가 필요합니다.",
            "SOCIAL_SIGNUP_NOT_FOUND", "소셜 인증 정보를 확인하지 못했습니다.",
            "SOCIAL_EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다. 기존 로그인 방식을 이용해 주세요.",
            "SOCIAL_ACCOUNT_CONFLICT", "이미 사용 중인 계정 정보가 있습니다."
    );

    @ExceptionHandler(SocialLoginException.class)
    public ResponseEntity<ProblemDetail> handleSocialSignupFailure(SocialLoginException exception) {
        HttpStatus status = switch (exception.code()) {
            case "SOCIAL_SIGNUP_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SOCIAL_ACCOUNT_CONFLICT", "SOCIAL_EMAIL_ALREADY_REGISTERED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                MESSAGES.getOrDefault(exception.code(), "소셜 회원가입을 완료하지 못했습니다.")
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
        problem.setProperty("code", "INVALID_SOCIAL_SIGNUP_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }
}
