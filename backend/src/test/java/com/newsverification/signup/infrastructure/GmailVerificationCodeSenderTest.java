/* Gmail 인증번호 메일 구성 검증 */
package com.newsverification.signup.infrastructure;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 수신자와 인증번호를 포함한 메일 발송 검증 */
class GmailVerificationCodeSenderTest {

    /** 회원가입 인증번호 메일 구성 */
    @Test
    void sendsSignupVerificationCode() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        var sender = new GmailVerificationCodeSender(mailSender, "sender@example.com");

        sender.sendSignupCode("user@example.com", "482916");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getValue().getTo()).containsExactly("user@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("기사체크 회원가입 인증번호");
        assertThat(message.getValue().getText()).contains("482916", "24시간");
    }
}
