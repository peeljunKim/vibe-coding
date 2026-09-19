/* 기사 제목 분석 이용량 Port */
package com.newsverification.headline.application;

/** 제목 분석 접수 한도와 실제 분석 시작 차감 경계 */
public interface HeadlineAnalysisUsagePolicy {

    /** 신규 제목 분석 접수 가능 여부 확인 */
    void verifyCanStart(HeadlineAnalysisUsageSubject subject);

    /** 실제 제목 분석 시작의 이용 횟수 차감 */
    HeadlineAnalysisUsageResult recordAnalysisStart(HeadlineAnalysisUsageSubject subject);
}
