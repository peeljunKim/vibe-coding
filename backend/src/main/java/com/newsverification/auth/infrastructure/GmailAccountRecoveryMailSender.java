/* Gmail 계정 복구 이메일 Adapter */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryMailSender;
import com.newsverification.auth.application.AccountRecoveryVerificationStore;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Gmail SMTP 기반 인증번호와 아이디 발송 */
public class GmailAccountRecoveryMailSender implements AccountRecoveryMailSender {

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public GmailAccountRecoveryMailSender(JavaMailSender mailSender, String senderEmail) {
        this.mailSender = Objects.requireNonNull(mailSender);
        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalArgumentException("MAIL_FROM is required");
        }
        this.senderEmail = senderEmail;
    }

    /** 계정 복구 인증번호 발송 */
    @Override
    public void sendVerificationCode(
            String email,
            AccountRecoveryVerificationStore.Purpose purpose,
            String code
    ) {
        String purposeLabel = purpose == AccountRecoveryVerificationStore.Purpose.USERNAME
                ? "아이디 찾기"
                : "비밀번호 재설정";
        send(
                email,
                "[기사체크] " + purposeLabel + " 인증번호를 확인해 주세요",
                purposeLabel + " 인증번호: " + code + "\n\n인증번호는 10분 동안 유효합니다.",
                recoveryHtml(purposeLabel, code)
        );
    }

    /** 전체 사용자 아이디 발송 */
    @Override
    public void sendUsername(String email, String username) {
        send(
                email,
                "[기사체크] 가입 아이디를 확인해 주세요",
                "기사체크 가입 아이디: " + username,
                usernameHtml(username)
        );
    }

    private void send(String email, String subject, String plainText, String htmlText) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(plainText, htmlText);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Failed to prepare account recovery email", exception);
        }
        mailSender.send(message);
    }

    private String recoveryHtml(String purposeLabel, String code) {
        return emailFrame(
                purposeLabel + " 인증번호",
                "<div style=\"font-size:40px;font-weight:700;letter-spacing:10px;\">" + code + "</div>"
                        + "<p style=\"margin:16px 0 0;color:#5e7079;\">10분 안에 입력해 주세요.</p>"
        );
    }

    private String usernameHtml(String username) {
        return emailFrame(
                "가입 아이디",
                "<div style=\"font-size:28px;font-weight:700;\">" + username + "</div>"
        );
    }

    private String emailFrame(String title, String content) {
        return """
                <!doctype html><html lang="ko"><body style="margin:0;background:#edf3f6;">
                <div style="max-width:560px;margin:32px auto;padding:40px;background:#fff;border-top:8px solid #17637a;
                border-radius:18px;font-family:'Malgun Gothic',Arial,sans-serif;color:#16242b;">
                <div style="font-size:25px;font-weight:700;color:#17637a;">기사체크</div>
                <h1 style="margin:28px 0 20px;font-size:26px;">{{TITLE}}</h1>
                <div style="padding:24px;border-radius:14px;background:#dff0f5;text-align:center;">{{CONTENT}}</div>
                <p style="margin:24px 0 0;font-size:13px;color:#5e7079;">요청하지 않았다면 이 메일을 무시해도 됩니다.</p>
                </div></body></html>
                """.replace("{{TITLE}}", title).replace("{{CONTENT}}", content);
    }
}
