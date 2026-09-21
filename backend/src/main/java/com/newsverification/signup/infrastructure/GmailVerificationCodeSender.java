/* Gmail 회원가입 인증번호 발송 Adapter */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.VerificationCodeSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

/** Gmail SMTP 기반 회원가입 인증번호 발송 */
public class GmailVerificationCodeSender implements VerificationCodeSender {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public GmailVerificationCodeSender(JavaMailSender mailSender, String senderEmail) {
        this.mailSender = Objects.requireNonNull(mailSender);
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalArgumentException("MAIL_FROM is required");
        }
        this.senderEmail = senderEmail;
    }

    /** 회원가입 인증번호 메일 발송 */
    @Override
    public void sendSignupCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject("기사체크 회원가입 인증번호");
        message.setText("기사체크 회원가입 인증번호는 " + code + "입니다. 24시간 안에 입력해 주세요.");
        mailSender.send(message);
    }
}
