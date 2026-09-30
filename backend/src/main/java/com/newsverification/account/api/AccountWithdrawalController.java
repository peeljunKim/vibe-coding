/* 회원 탈퇴 신청 API */
package com.newsverification.account.api;

import com.newsverification.account.application.AccountWithdrawalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 탈퇴 유예 시작 HTTP Adapter */
@RestController
@RequestMapping("/api/account/withdrawal")
public class AccountWithdrawalController {

    private final AccountWithdrawalService service;

    public AccountWithdrawalController(AccountWithdrawalService service) {
        this.service = service;
    }

    /** 탈퇴 신청과 복구·삭제 일정 반환 */
    @PostMapping
    public ResponseEntity<AccountWithdrawalService.Withdrawal> request(Authentication authentication) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.request(authentication.getName()));
    }
}
