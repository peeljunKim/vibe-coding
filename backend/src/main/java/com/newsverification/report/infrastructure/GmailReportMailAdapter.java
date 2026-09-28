/* Gmail 문제 신고 알림 Adapter */
package com.newsverification.report.infrastructure;

import com.newsverification.report.application.ReportMailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Gmail SMTP 기반 신고 접수와 처리 완료 알림 */
public class GmailReportMailAdapter implements ReportMailPort {

    private final JavaMailSender mailSender;
    private final String senderEmail;
    private final String administratorEmail;

    public GmailReportMailAdapter(
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

    /** 신규 신고 관리자 알림 */
    @Override
    public void notifyNewReport(long reportId) {
        send(
                administratorEmail,
                "[기사체크] 신규 신고 #" + reportId,
                "신규 신고 #" + reportId + "이 접수되었습니다. 관리자 화면에서 확인해 주세요."
        );
    }

    /** 신고 처리 완료 사용자 알림 */
    @Override
    public void notifyResolved(String recipientEmail, long reportId) {
        send(
                required(recipientEmail, "Report recipient email is required"),
                "[기사체크] 신고 #" + reportId + " 처리 완료",
                "접수한 신고 #" + reportId + "의 처리가 완료되었습니다. 기사체크에서 확인해 주세요."
        );
    }

    private void send(String recipient, String subject, String body) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name()
            );
            helper.setFrom(senderEmail);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Failed to prepare report email", exception);
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
