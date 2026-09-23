/* Gmail 계정 복구 메일 구성 검증 */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryVerificationStore;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 인증번호와 전체 아이디 메일 발송 검증 */
class GmailAccountRecoveryMailSenderTest {

    /** 비밀번호 재설정 인증번호 메일 구성 */
    @Test
    void sendsPasswordRecoveryCode() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender = new GmailAccountRecoveryMailSender(mailSender, "sender@example.com");

        sender.sendVerificationCode(
                "user@example.com",
                AccountRecoveryVerificationStore.Purpose.PASSWORD,
                "482916"
        );

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).contains("비밀번호 재설정");
        assertThat(findBody(message, "text/plain")).contains("482916", "10분");
        assertThat(findBody(message, "text/html"))
                .contains("기사체크", "482916", "10분")
                .doesNotContain("<script");
    }

    /** 전체 사용자 아이디 메일 구성 */
    @Test
    void sendsRecoveredUsername() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender = new GmailAccountRecoveryMailSender(mailSender, "sender@example.com");

        sender.sendUsername("user@example.com", "healthcheck26");

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).contains("가입 아이디");
        assertThat(findBody(message, "text/plain")).contains("healthcheck26");
        assertThat(findBody(message, "text/html")).contains("healthcheck26");
    }

    /** MIME 본문 유형 조회 */
    private static String findBody(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part.getContent().toString();
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                String body = findBody(multipart.getBodyPart(index), mimeType);
                if (!body.isEmpty()) {
                    return body;
                }
            }
        }
        return "";
    }
}
