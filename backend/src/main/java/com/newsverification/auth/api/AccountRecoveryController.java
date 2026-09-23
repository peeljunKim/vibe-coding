/* 이메일 기반 계정 복구 API */
package com.newsverification.auth.api;

import com.newsverification.auth.application.AccountRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 아이디 찾기와 비밀번호 재설정 HTTP Adapter */
@RestController
@RequestMapping("/api/auth/recovery")
public class AccountRecoveryController {

    private static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{10,}$";

    private final AccountRecoveryService recoveryService;

    public AccountRecoveryController(AccountRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /** 아이디 찾기 인증번호 요청 */
    @PostMapping("/username/code")
    public ResponseEntity<RecoveryRequestResponse> requestUsernameCode(
            @Valid @RequestBody EmailRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(RecoveryRequestResponse.from(recoveryService.requestUsernameCode(request.email())));
    }

    /** 아이디 찾기 인증번호 확인 */
    @PostMapping("/username/verify")
    public RecoveredUsernameResponse verifyUsernameCode(
            @Valid @RequestBody VerificationRequest request
    ) {
        return RecoveredUsernameResponse.from(
                recoveryService.verifyUsernameCode(request.email(), request.code())
        );
    }

    /** 비밀번호 재설정 인증번호 요청 */
    @PostMapping("/password/code")
    public ResponseEntity<RecoveryRequestResponse> requestPasswordCode(
            @Valid @RequestBody EmailRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(RecoveryRequestResponse.from(recoveryService.requestPasswordCode(request.email())));
    }

    /** 비밀번호 재설정과 기존 Session 만료 */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recoveryService.resetPassword(request.toCommand());
        return ResponseEntity.noContent().build();
    }

    /** 계정 복구 이메일 입력 */
    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {
        @Override
        public String toString() {
            return "EmailRequest[redacted]";
        }
    }

    /** 계정 복구 인증번호 입력 */
    public record VerificationRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {
        @Override
        public String toString() {
            return "VerificationRequest[redacted]";
        }
    }

    /** 비밀번호 재설정 입력 */
    public record ResetPasswordRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
            @NotBlank @Pattern(regexp = PASSWORD_PATTERN) String password,
            @NotBlank String passwordConfirm
    ) {
        AccountRecoveryService.ResetPasswordCommand toCommand() {
            return new AccountRecoveryService.ResetPasswordCommand(email, code, password, passwordConfirm);
        }

        @Override
        public String toString() {
            return "ResetPasswordRequest[redacted]";
        }
    }

    /** 인증번호 요청 결과 */
    public record RecoveryRequestResponse(int remainingAttempts, long resendAvailableInSeconds) {
        static RecoveryRequestResponse from(AccountRecoveryService.RecoveryRequest result) {
            return new RecoveryRequestResponse(result.remainingAttempts(), result.resendAvailableInSeconds());
        }
    }

    /** 일부 마스킹 아이디 응답 */
    public record RecoveredUsernameResponse(String maskedUsername) {
        static RecoveredUsernameResponse from(AccountRecoveryService.RecoveredUsername result) {
            return new RecoveredUsernameResponse(result.maskedUsername());
        }
    }
}
