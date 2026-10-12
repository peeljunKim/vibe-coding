/* 언론사 자동 중단 프로필 구성 검증 */
package com.newsverification.config;

import com.newsverification.publisher.application.PublisherAlertPort;
import com.newsverification.publisher.application.PublisherExtractionAttemptStore;
import com.newsverification.publisher.application.PublisherExtractionPolicy;
import com.newsverification.publisher.application.PublisherStatusUpdater;
import com.newsverification.publisher.infrastructure.GmailPublisherAlertAdapter;
import com.newsverification.publisher.infrastructure.MockPublisherAlertAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 기본·SMTP·운영 프로필의 언론사 알림 Bean 검증 */
class PublisherExtractionPolicyConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PublisherExtractionPolicyConfig.class)
            .withBean(
                    PublisherExtractionAttemptStore.class,
                    () -> mock(PublisherExtractionAttemptStore.class)
            )
            .withBean(PublisherStatusUpdater.class, () -> mock(PublisherStatusUpdater.class))
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues(
                    "MAIL_FROM=sender@example.com",
                    "REPORT_ADMIN_EMAIL=admin@example.com"
            );

    /** 기본 프로필 Mock Adapter 구성 */
    @Test
    void wiresMockAdapterByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PublisherExtractionPolicy.class);
            assertThat(context.getBean(PublisherAlertPort.class))
                    .isInstanceOf(MockPublisherAlertAdapter.class);
        });
    }

    /** SMTP 프로필 Gmail Adapter 구성 */
    @Test
    void wiresGmailAdapterForSmtpProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=smtp")
                .run(context -> assertThat(context.getBean(PublisherAlertPort.class))
                        .isInstanceOf(GmailPublisherAlertAdapter.class));
    }

    /** 운영 프로필 Gmail Adapter 구성 */
    @Test
    void wiresGmailAdapterForProdProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context.getBean(PublisherAlertPort.class))
                        .isInstanceOf(GmailPublisherAlertAdapter.class));
    }
}
