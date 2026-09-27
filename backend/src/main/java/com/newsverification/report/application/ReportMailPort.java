/* 문제 신고 알림 메일 Port */
package com.newsverification.report.application;

/** 관리자 접수 알림과 사용자 완료 알림 경계 */
public interface ReportMailPort {

    void notifyNewReport(long reportId);

    void notifyResolved(String recipientEmail, long reportId);
}
