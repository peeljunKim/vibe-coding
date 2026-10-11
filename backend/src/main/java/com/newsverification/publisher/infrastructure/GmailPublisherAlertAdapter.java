/* Gmail 언론사 자동 중단 알림 Adapter */
package com.newsverification.publisher.infrastructure;

import com.newsverification.publisher.application.PublisherAlertPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Gmail SMTP 기반 관리자 운영 알림 */
public class GmailPublisherAlertAdapter implements PublisherAlertPort {

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String administratorEmail;

    public GmailPublisherAlertAdapter(
            JavaMailSender mailSender,
            String senderEmail,
            String administratorEmail
    ) {
        this.mailSender = Objects.requireNonNull(mailSender);
        this.senderEmail = required(senderEmail, "MAIL_FROM is required");
        this.administratorEmail = required(
                administratorEmail, "REPORT_ADMIN_EMAIL is required"
        );
    }

    /** 자동 중단 관리자 알림 */
    @Override
    public void notifyAutomaticallyPaused(String publisherName) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name()
            );
            helper.setFrom(senderEmail);
            helper.setTo(administratorEmail);
            helper.setSubject("[기사체크] 언론사 기사 추출 자동 중단");
            helper.setText(
                    required(publisherName, "Publisher name is required")
                            + "의 기사 추출 실패율이 기준을 넘어 자동 중단되었습니다."
            );
        } catch (MessagingException exception) {
            throw new MailPreparationException(
                    "Failed to prepare publisher alert email", exception
            );
        }
        mailSender.send(message);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
