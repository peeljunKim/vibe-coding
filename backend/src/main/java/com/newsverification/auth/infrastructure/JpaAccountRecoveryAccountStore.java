/* JPA 계정 복구 사용자 Adapter */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.AccountRecoveryAccountStore;
import com.newsverification.auth.application.AccountRecoveryException;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 활성 일반 계정 조회와 비밀번호 변경 */
@Repository
public class JpaAccountRecoveryAccountStore implements AccountRecoveryAccountStore {

    private final UserAccountRepository repository;

    public JpaAccountRecoveryAccountStore(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findActiveLocalByEmail(String email) {
        return repository.findByEmailAndAccountTypeAndStatus(
                email,
                "LOCAL",
                UserAccount.UserStatus.ACTIVE
        ).map(this::toAccount);
    }

    @Override
    @Transactional
    public void changePassword(long userId, String passwordHash) {
        UserAccount account = repository.findActiveLocalByIdForUpdate(
                        userId,
                        "LOCAL",
                        UserAccount.UserStatus.ACTIVE
                )
                .orElseThrow(() -> new AccountRecoveryException("INVALID_VERIFICATION_CODE"));
        account.changePasswordHash(passwordHash);
    }

    private Account toAccount(UserAccount account) {
        return new Account(account.id(), account.username(), account.email());
    }
}
