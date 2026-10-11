/* Gmail 언론사 자동 중단 메일 검증 */
package com.newsverification.publisher.infrastructure;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 관리자 수신자와 중단 대상 표시 검증 */
class GmailPublisherAlertAdapterTest {

    /** 자동 중단 관리자 알림 */
    @Test
    void sendsAutomaticPauseNoticeToConfiguredAdministrator() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var adapter = new GmailPublisherAlertAdapter(
                mailSender, "sender@example.com", "admin@example.com"
        );

        adapter.notifyAutomaticallyPaused("테스트 언론사");

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("admin@example.com");
        assertThat(message.getSubject()).contains("기사 추출", "자동 중단");
        assertThat(message.getContent().toString()).contains("테스트 언론사");
    }
}
