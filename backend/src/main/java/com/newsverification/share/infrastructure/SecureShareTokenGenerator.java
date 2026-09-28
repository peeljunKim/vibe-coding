/* 공유 토큰 보안 생성기 */
package com.newsverification.share.infrastructure;

import com.newsverification.share.application.ShareTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** 256bit Base64URL 공유 토큰 생성 */
@Component
public class SecureShareTokenGenerator implements ShareTokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
