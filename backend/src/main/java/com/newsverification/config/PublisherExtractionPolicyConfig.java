/* 언론사 추출 실패 자동 중단 구성 */
package com.newsverification.config;

import com.newsverification.publisher.application.PublisherAlertPort;
import com.newsverification.publisher.application.PublisherAutoPausePolicy;
import com.newsverification.publisher.application.PublisherExtractionAttemptStore;
import com.newsverification.publisher.application.PublisherExtractionPolicy;
import com.newsverification.publisher.application.PublisherStatusUpdater;
import com.newsverification.publisher.infrastructure.GmailPublisherAlertAdapter;
import com.newsverification.publisher.infrastructure.MockPublisherAlertAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/** Redis 집계와 언론사 상태·알림 연결 */
@Configuration
public class PublisherExtractionPolicyConfig {

    @Bean
    PublisherExtractionPolicy publisherExtractionPolicy(
            PublisherExtractionAttemptStore attemptStore,
            PublisherStatusUpdater statusUpdater,
            PublisherAlertPort alertPort
    ) {
        return new PublisherAutoPausePolicy(attemptStore, statusUpdater, alertPort);
    }

    @Bean
    @Profile("!prod & !smtp")
    PublisherAlertPort mockPublisherAlertPort() {
        return new MockPublisherAlertAdapter();
    }

    @Bean
    @Profile({"prod", "smtp"})
    PublisherAlertPort gmailPublisherAlertPort(
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String senderEmail,
            @Value("${REPORT_ADMIN_EMAIL:}") String administratorEmail
    ) {
        return new GmailPublisherAlertAdapter(mailSender, senderEmail, administratorEmail);
    }
}
