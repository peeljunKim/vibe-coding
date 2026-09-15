/* 건강 분석 이용자 유형과 일일 한도 */
package com.newsverification.health.application;

/** 회원·비회원별 건강 분석 일일 한도 */
public enum HealthAnalysisUserType {
    MEMBER(5),
    GUEST(2);

    private final int dailyLimit;

    /** 이용자 유형별 일일 한도 구성 */
    HealthAnalysisUserType(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 건강 분석 일일 허용 횟수 */
    public int dailyLimit() {
        return dailyLimit;
    }
}
