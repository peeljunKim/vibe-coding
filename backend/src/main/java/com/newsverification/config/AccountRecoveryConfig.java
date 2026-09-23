/* 계정 복구 실행 구성 */
package com.newsverification.config;

import com.newsverification.auth.application.AccountRecoveryAccountStore;
import com.newsverification.auth.application.AccountRecoveryMailSender;
import com.newsverification.auth.application.AccountRecoveryService;
import com.newsverification.auth.application.AccountRecoveryVerificationStore;
import com.newsverification.auth.application.AccountSessionInvalidator;
import com.newsverification.auth.application.DefaultAccountRecoveryService;
import com.newsverification.auth.infrastructure.GmailAccountRecoveryMailSender;
import com.newsverification.auth.infrastructure.MockAccountRecoveryMailSender;
import com.newsverification.auth.infrastructure.RedisAccountRecoveryVerificationStore;
import com.newsverification.auth.infrastructure.SpringSessionAccountSessionInvalidator;
import com.newsverification.signup.application.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;

/** 이메일 기반 계정 복구 구성 */
@Configuration
public class AccountRecoveryConfig {

    @Bean
    AccountRecoveryVerificationStore accountRecoveryVerificationStore(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.redis.key-prefix}") String keyPrefix,
            @Value("${app.time-zone:Asia/Seoul}") ZoneId zoneId,
            Clock clock
    ) {
        return new RedisAccountRecoveryVerificationStore(
                redisTemplate,
                passwordEncoder,
                keyPrefix,
                zoneId,
                clock
        );
    }

    @Bean
    AccountSessionInvalidator accountSessionInvalidator(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        return new SpringSessionAccountSessionInvalidator(sessionRepository);
    }

    @Bean
    @Profile("!prod & !smtp")
    AccountRecoveryMailSender mockAccountRecoveryMailSender() {
        return new MockAccountRecoveryMailSender();
    }

    @Bean
    @Profile({"prod", "smtp"})
    AccountRecoveryMailSender gmailAccountRecoveryMailSender(
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String senderEmail
    ) {
        return new GmailAccountRecoveryMailSender(mailSender, senderEmail);
    }

    @Bean
    AccountRecoveryService accountRecoveryService(
            AccountRecoveryAccountStore accountStore,
            AccountRecoveryVerificationStore verificationStore,
            AccountRecoveryMailSender mailSender,
            VerificationCodeGenerator codeGenerator,
            PasswordEncoder passwordEncoder,
            AccountSessionInvalidator sessionInvalidator,
            @Value("${app.analysis.lookup-hmac-key}") String lookupHmacKey
    ) {
        return new DefaultAccountRecoveryService(
                accountStore,
                verificationStore,
                mailSender,
                codeGenerator,
                passwordEncoder,
                sessionInvalidator,
                lookupHmacKey.getBytes(StandardCharsets.UTF_8)
        );
    }
}
