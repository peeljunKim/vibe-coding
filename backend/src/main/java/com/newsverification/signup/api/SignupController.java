/* 일반 회원가입과 이메일 인증 API */
package com.newsverification.signup.api;

import com.newsverification.signup.application.SignupException;
import com.newsverification.signup.application.SignupService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 비로그인 회원가입 흐름 HTTP Adapter */
@RestController
@RequestMapping("/api/signup")
public class SignupController {

    static final String PENDING_SIGNUP_USER_ID = "pendingSignupUserId";
    private static final String USERNAME_PATTERN = "^(?=.*[a-z])(?=.*[0-9])[a-z0-9]{5,20}$";
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{10,}$";
    private static final String PHONE_PATTERN = "^010-[0-9]{4}-[0-9]{4}$";

    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    /** 미인증 일반 계정 생성과 인증번호 발급 */
    @PostMapping
    public ResponseEntity<PendingSignupResponse> register(
            @Valid @RequestBody SignupRequest request,
            HttpSession session
    ) {
        SignupService.PendingSignup pending = signupService.register(request.toCommand());
        session.setAttribute(PENDING_SIGNUP_USER_ID, pending.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PendingSignupResponse.from(pending));
    }

    /** 이메일 인증번호 확인 */
    @PostMapping("/email-verification")
    public CompletedSignupResponse verifyEmail(
            @Valid @RequestBody VerificationRequest request,
            HttpSession session
    ) {
        requirePendingSignupOwner(session, request.userId());
        CompletedSignupResponse completed = CompletedSignupResponse.from(
                signupService.verifyEmail(request.toCommand())
        );
        session.removeAttribute(PENDING_SIGNUP_USER_ID);
        return completed;
    }

    /** 이메일 인증번호 재발급 */
    @PostMapping("/email-verification/resend")
    public PendingSignupResponse resend(
            @Valid @RequestBody ResendRequest request,
            HttpSession session
    ) {
        requirePendingSignupOwner(session, request.userId());
        return PendingSignupResponse.from(signupService.resendVerification(request.userId()));
    }

    private void requirePendingSignupOwner(HttpSession session, long userId) {
        Object pendingUserId = session.getAttribute(PENDING_SIGNUP_USER_ID);
        if (!(pendingUserId instanceof Long ownedUserId) || ownedUserId != userId) {
            throw new SignupException("SIGNUP_NOT_FOUND");
        }
    }

    /** 회원가입 요청 형식 */
    public record SignupRequest(
            @NotBlank String inviteCode,
            @NotBlank @Pattern(regexp = USERNAME_PATTERN) String username,
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN) String password,
            @NotBlank String passwordConfirm,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = PHONE_PATTERN) String phoneNumber,
            @AssertTrue boolean agreementsAccepted
    ) {
        SignupService.RegisterCommand toCommand() {
            return new SignupService.RegisterCommand(
                    inviteCode,
                    username,
                    password,
                    passwordConfirm,
                    email,
                    phoneNumber,
                    agreementsAccepted
            );
        }

        @Override
        public String toString() {
            return "SignupRequest[redacted]";
        }
    }

    /** 이메일 인증번호 요청 형식 */
    public record VerificationRequest(
            @Positive long userId,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {
        SignupService.VerifyCommand toCommand() {
            return new SignupService.VerifyCommand(userId, code);
        }

        @Override
        public String toString() {
            return "VerificationRequest[redacted]";
        }
    }

    /** 인증번호 재발송 요청 형식 */
    public record ResendRequest(@Positive long userId) {
    }

    /** 이메일 인증 대기 응답 */
    public record PendingSignupResponse(
            long userId,
            String username,
            String email,
            int remainingAttempts,
            long resendAvailableInSeconds
    ) {
        static PendingSignupResponse from(SignupService.PendingSignup pending) {
            return new PendingSignupResponse(
                    pending.userId(),
                    pending.username(),
                    pending.email(),
                    pending.remainingAttempts(),
                    pending.resendAvailableInSeconds()
            );
        }
    }

    /** 회원가입 완료 응답 */
    public record CompletedSignupResponse(long userId, String username, String email) {
        static CompletedSignupResponse from(SignupService.CompletedSignup completed) {
            return new CompletedSignupResponse(completed.userId(), completed.username(), completed.email());
        }
    }
}
