/* 회원 탈퇴 API 오류 응답 변환 */
package com.newsverification.account.api;

import com.newsverification.account.application.AccountWithdrawalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 탈퇴 신청 상태 충돌의 공개 오류 변환 */
@RestControllerAdvice(assignableTypes = AccountWithdrawalController.class)
public class AccountWithdrawalErrorHandler {

    @ExceptionHandler(AccountWithdrawalException.class)
    public ResponseEntity<ProblemDetail> handleConflict() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "현재 계정 상태에서는 탈퇴를 신청할 수 없습니다."
        );
        problem.setTitle("Account withdrawal unavailable");
        problem.setProperty("code", "ACCOUNT_NOT_ACTIVE");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
