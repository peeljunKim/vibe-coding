/* 기사 제목 분석 이용자 유형과 일일 한도 */
package com.newsverification.headline.application;

/** 회원·비회원별 제목 분석 일일 한도 */
public enum HeadlineAnalysisUserType {
    MEMBER(10),
    GUEST(5);

    private final int dailyLimit;

    /** 이용자 유형별 일일 한도 구성 */
    HeadlineAnalysisUserType(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    /** 제목 분석 일일 허용 횟수 */
    public int dailyLimit() {
        return dailyLimit;
    }
}
