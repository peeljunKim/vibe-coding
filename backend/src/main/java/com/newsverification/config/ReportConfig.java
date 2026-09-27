/* 문제 신고 알림 실행 구성 */
package com.newsverification.config;

import com.newsverification.report.application.ReportMailPort;
import com.newsverification.report.infrastructure.GmailReportMailAdapter;
import com.newsverification.report.infrastructure.MockReportMailAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/** 실제 SMTP 호출 없는 신고 메일 연결 */
@Configuration
public class ReportConfig {

    @Bean
    @Profile("!prod & !smtp")
    ReportMailPort mockReportMailPort() {
        return new MockReportMailAdapter();
    }

    @Bean
    @Profile({"prod", "smtp"})
    ReportMailPort gmailReportMailPort(
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String senderEmail,
            @Value("${REPORT_ADMIN_EMAIL:}") String administratorEmail
    ) {
        return new GmailReportMailAdapter(mailSender, senderEmail, administratorEmail);
    }
}
