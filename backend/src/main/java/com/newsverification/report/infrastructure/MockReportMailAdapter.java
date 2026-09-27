/* Local 문제 신고 이메일 Mock */
package com.newsverification.report.infrastructure;

import com.newsverification.report.application.ReportMailPort;

/** 실제 SMTP 호출 없는 신고 알림 Adapter */
public class MockReportMailAdapter implements ReportMailPort {

    @Override
    public void notifyNewReport(long reportId) {
    }

    @Override
    public void notifyResolved(String recipientEmail, long reportId) {
    }
}
