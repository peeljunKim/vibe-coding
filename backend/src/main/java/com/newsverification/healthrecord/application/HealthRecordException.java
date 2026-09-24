/* 저장 건강 분석 공개 오류 */
package com.newsverification.healthrecord.application;

/** 저장·조회 조건 불충족 오류 */
public class HealthRecordException extends RuntimeException {

    public HealthRecordException(String code) {
        super(code);
    }
}
