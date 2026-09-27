/* 문제 신고 업무 예외 */
package com.newsverification.report.application;

/** 공개 오류 코드만 전달하는 신고 예외 */
public class ReportException extends RuntimeException {

    public ReportException(String code) {
        super(code);
    }
}
