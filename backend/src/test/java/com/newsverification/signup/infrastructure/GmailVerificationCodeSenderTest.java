/* Gmail 인증번호 메일 구성 검증 */
package com.newsverification.signup.infrastructure;

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

/** 수신자와 인증번호를 포함한 메일 발송 검증 */
class GmailVerificationCodeSenderTest {

    /** 회원가입 인증번호 메일 구성 */
    @Test
    void sendsSignupVerificationCode() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender = new GmailVerificationCodeSender(mailSender, "sender@example.com");

        sender.sendSignupCode("user@example.com", "482916");

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("sender@example.com");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("[기사체크] 회원가입 인증번호를 확인해 주세요");

        String plainText = findBody(message, "text/plain");
        assertThat(plainText)
                .contains("482916", "24시간", "인증번호를 다른 사람에게 알려주지 마세요")
                .doesNotContain("Hash", "서버에는 인증번호 원문");

        String htmlText = findBody(message, "text/html");
        assertThat(htmlText)
                .contains("기사체크", "482916", "#17637a")
                .contains("24시간", "인증번호를 다른 사람에게 알려주지 마세요")
                .doesNotContain("번호 영역을 선택해 복사해 주세요", "Hash", "서버에는 인증번호 원문", "<script");
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
