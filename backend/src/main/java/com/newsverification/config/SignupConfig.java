/* 일반 회원가입 실행 구성 */
package com.newsverification.config;

import com.newsverification.signup.application.DefaultSignupService;
import com.newsverification.signup.application.EmailVerificationStore;
import com.newsverification.signup.application.PendingSignupCleanupService;
import com.newsverification.signup.application.SignupAccountStore;
import com.newsverification.signup.application.SignupService;
import com.newsverification.signup.application.VerificationCodeGenerator;
import com.newsverification.signup.application.VerificationCodeSender;
import com.newsverification.signup.infrastructure.GmailVerificationCodeSender;
import com.newsverification.signup.infrastructure.MockVerificationCodeSender;
import com.newsverification.signup.infrastructure.RedisEmailVerificationStore;
import com.newsverification.signup.infrastructure.SecureVerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/** 일반 회원가입과 이메일 발송 연결 */
@Configuration
@EnableScheduling
public class SignupConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    VerificationCodeGenerator verificationCodeGenerator() {
        return new SecureVerificationCodeGenerator();
    }

    @Bean
    @Profile("!prod & !smtp")
    VerificationCodeSender mockVerificationCodeSender() {
        return new MockVerificationCodeSender();
    }

    @Bean
    @Profile({"prod", "smtp"})
    VerificationCodeSender gmailVerificationCodeSender(
            JavaMailSender mailSender,
            @Value("${MAIL_FROM:${spring.mail.username:}}") String senderEmail
    ) {
        return new GmailVerificationCodeSender(mailSender, senderEmail);
    }

    @Bean
    EmailVerificationStore emailVerificationStore(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${app.redis.key-prefix}") String keyPrefix,
            @Value("${app.time-zone:Asia/Seoul}") ZoneId zoneId,
            Clock clock
    ) {
        return new RedisEmailVerificationStore(redisTemplate, passwordEncoder, keyPrefix, zoneId, clock);
    }

    @Bean
    SignupService signupService(
            @Value("${INVITE_CODE_1:}") String inviteCode1,
            @Value("${INVITE_CODE_2:}") String inviteCode2,
            @Value("${INVITE_CODE_3:}") String inviteCode3,
            @Value("${INVITE_CODE_4:}") String inviteCode4,
            @Value("${INVITE_CODE_5:}") String inviteCode5,
            SignupAccountStore accountStore,
            EmailVerificationStore verificationStore,
            VerificationCodeSender codeSender,
            VerificationCodeGenerator codeGenerator,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        return new DefaultSignupService(
                List.of(inviteCode1, inviteCode2, inviteCode3, inviteCode4, inviteCode5),
                accountStore,
                verificationStore,
                codeSender,
                codeGenerator,
                passwordEncoder,
                clock
        );
    }

    @Bean
    PendingSignupCleanupService pendingSignupCleanupService(
            SignupAccountStore accountStore,
            Clock clock
    ) {
        return new PendingSignupCleanupService(accountStore, clock);
    }
}
