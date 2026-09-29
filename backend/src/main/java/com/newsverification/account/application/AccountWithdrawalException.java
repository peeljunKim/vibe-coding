/* 회원 탈퇴 업무 오류 */
package com.newsverification.account.application;

/** 공개 오류 코드 전달 */
public class AccountWithdrawalException extends RuntimeException {

    public AccountWithdrawalException(String code) {
        super(code);
    }
}
