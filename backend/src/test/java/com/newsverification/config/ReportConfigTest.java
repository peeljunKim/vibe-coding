/* 문제 신고 메일 프로필 구성 검증 */
package com.newsverification.config;

import com.newsverification.report.application.ReportMailPort;
import com.newsverification.report.infrastructure.GmailReportMailAdapter;
import com.newsverification.report.infrastructure.MockReportMailAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 기본·SMTP·운영 프로필의 신고 메일 Bean 검증 */
class ReportConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReportConfig.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues(
                    "MAIL_FROM=sender@example.com",
                    "REPORT_ADMIN_EMAIL=admin@example.com"
            );

    /** 기본 프로필 Mock Adapter 구성 */
    @Test
    void wiresMockAdapterByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReportMailPort.class);
            assertThat(context.getBean(ReportMailPort.class)).isInstanceOf(MockReportMailAdapter.class);
        });
    }

    /** SMTP 프로필 Gmail Adapter 구성 */
    @Test
    void wiresGmailAdapterForSmtpProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=smtp")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReportMailPort.class);
                    assertThat(context.getBean(ReportMailPort.class)).isInstanceOf(GmailReportMailAdapter.class);
                });
    }

    /** 운영 프로필 Gmail Adapter 구성 */
    @Test
    void wiresGmailAdapterForProdProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReportMailPort.class);
                    assertThat(context.getBean(ReportMailPort.class)).isInstanceOf(GmailReportMailAdapter.class);
                });
    }
}
