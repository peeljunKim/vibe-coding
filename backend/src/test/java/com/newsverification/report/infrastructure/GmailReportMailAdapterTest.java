/* Gmail 문제 신고 메일 구성 검증 */
package com.newsverification.report.infrastructure;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 관리자 접수와 사용자 처리 완료 메일 검증 */
class GmailReportMailAdapterTest {

    /** 신규 신고 관리자 알림 */
    @Test
    void sendsNewReportNoticeToConfiguredAdministrator() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var adapter = new GmailReportMailAdapter(
                mailSender, "sender@example.com", "admin@example.com"
        );

        adapter.notifyNewReport(17L);

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("admin@example.com");
        assertThat(message.getSubject()).contains("신규 신고", "17");
    }

    /** 처리 완료 사용자 알림 */
    @Test
    void sendsResolutionNoticeWithoutAdministratorReply() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var adapter = new GmailReportMailAdapter(
                mailSender, "sender@example.com", "admin@example.com"
        );

        adapter.notifyResolved("member@example.com", 17L);

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("member@example.com");
        assertThat(message.getSubject()).contains("처리 완료", "17");
        assertThat(message.getContent().toString())
                .contains("처리가 완료되었습니다")
                .doesNotContain("관리자 답변");
    }
}
