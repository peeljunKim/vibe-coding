/* Local 회원가입 이메일 발송 Mock */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.VerificationCodeSender;

/** 외부 SMTP 호출 없는 개발용 발송 Adapter */
public class MockVerificationCodeSender implements VerificationCodeSender {

    @Override
    public void sendSignupCode(String email, String code) {
        // 외부 발송 없는 Local Mock
    }
}
