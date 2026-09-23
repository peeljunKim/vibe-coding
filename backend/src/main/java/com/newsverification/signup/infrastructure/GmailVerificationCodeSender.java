/* Gmail 회원가입 인증번호 발송 Adapter */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.VerificationCodeSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Gmail SMTP 기반 회원가입 인증번호 발송 */
public class GmailVerificationCodeSender implements VerificationCodeSender {

    private static final String SUBJECT = "[기사체크] 회원가입 인증번호를 확인해 주세요";
    private static final String PLAIN_TEXT_TEMPLATE = """
            기사체크 회원가입 인증번호를 확인해 주세요.

            6자리 인증번호: {{CODE}}

            인증번호는 24시간 동안 유효합니다.
            인증번호를 다른 사람에게 알려주지 마세요.
            요청하지 않았다면 이 메일을 무시해도 됩니다.

            본 메일은 발신 전용입니다.
            """;
    private static final String HTML_TEXT_TEMPLATE = """
            <!doctype html>
            <html lang="ko">
            <body style="margin:0;padding:0;background:#edf3f6;color:#16242b;">
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
                     style="width:100%;background:#edf3f6;">
                <tr>
                  <td align="center" style="padding:32px 16px;">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"
                           style="width:100%;max-width:600px;background:#ffffff;border:1px solid #d7e1e6;
                                  border-top:8px solid #17637a;border-radius:18px;">
                      <tr>
                        <td style="padding:44px 48px 40px;font-family:'Malgun Gothic',Arial,sans-serif;">
                          <div style="font-size:26px;line-height:1.3;font-weight:700;color:#17637a;">기사체크</div>
                          <div style="display:inline-block;margin-top:28px;padding:7px 18px;border-radius:18px;
                                      background:#dff0f5;color:#17637a;font-size:13px;font-weight:700;">
                            이메일 인증
                          </div>
                          <h1 style="margin:34px 0 0;font-size:28px;line-height:1.45;color:#16242b;">
                            회원가입 인증번호를<br>확인해 주세요
                          </h1>
                          <p style="margin:18px 0 0;font-size:15px;line-height:1.7;color:#5e7079;">
                            기사체크 회원가입을 계속하려면 아래 번호를 입력하세요.
                          </p>
                          <div style="margin-top:30px;padding:26px 20px;border-radius:16px;background:#dff0f5;
                                      text-align:center;">
                            <div style="font-size:13px;font-weight:700;color:#17637a;">6자리 인증번호</div>
                            <div style="margin-top:12px;font-family:Consolas,'Courier New',monospace;font-size:42px;
                                        line-height:1.3;font-weight:700;letter-spacing:10px;color:#16242b;
                                        user-select:all;">{{CODE}}</div>
                          </div>
                          <div style="margin-top:28px;padding:18px 20px;border:1px solid #f1d9a7;
                                      border-radius:12px;background:#fff8e8;">
                            <div style="font-size:14px;font-weight:700;color:#a85a00;">24시간 안에 입력해 주세요</div>
                            <div style="margin-top:6px;font-size:13px;line-height:1.6;color:#5e7079;">
                              요청하지 않았다면 이 메일을 무시해도 됩니다.
                            </div>
                          </div>
                          <div style="margin-top:30px;padding-top:24px;border-top:1px solid #d7e1e6;">
                            <div style="font-size:13px;font-weight:700;color:#16242b;">
                              인증번호를 다른 사람에게 알려주지 마세요.
                            </div>
                            <div style="margin-top:18px;font-size:12px;color:#5e7079;">본 메일은 발신 전용입니다.</div>
                          </div>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;

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
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail);
            helper.setTo(email);
            helper.setSubject(SUBJECT);
            helper.setText(
                    PLAIN_TEXT_TEMPLATE.replace("{{CODE}}", code),
                    HTML_TEXT_TEMPLATE.replace("{{CODE}}", code)
            );
        } catch (MessagingException exception) {
            throw new MailPreparationException("Failed to prepare signup verification email", exception);
        }
        mailSender.send(message);
    }
}
