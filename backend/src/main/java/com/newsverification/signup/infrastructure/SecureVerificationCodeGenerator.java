/* 보안 난수 기반 이메일 인증번호 생성 */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.VerificationCodeGenerator;

import java.security.SecureRandom;

/** 6자리 숫자 인증번호 생성 */
public class SecureVerificationCodeGenerator implements VerificationCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }
}
