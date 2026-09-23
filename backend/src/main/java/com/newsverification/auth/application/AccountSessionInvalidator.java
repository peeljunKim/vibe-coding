/* 계정 Session 만료 Port */
package com.newsverification.auth.application;

/** 사용자 계정의 전체 로그인 Session 만료 경계 */
public interface AccountSessionInvalidator {

    void invalidateAll(long userId);
}
