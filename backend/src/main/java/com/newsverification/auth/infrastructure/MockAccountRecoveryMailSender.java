/* Local 계정 복구 이메일 Mock */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryMailSender;
import com.newsverification.auth.application.AccountRecoveryVerificationStore;

/** 외부 발송 없는 Local 계정 복구 이메일 Adapter */
public class MockAccountRecoveryMailSender implements AccountRecoveryMailSender {

    @Override
    public void sendVerificationCode(
            String email,
            AccountRecoveryVerificationStore.Purpose purpose,
            String code
    ) {
    }

    @Override
    public void sendUsername(String email, String username) {
    }
}
