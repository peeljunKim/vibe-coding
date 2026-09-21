/* JPA 회원가입 계정 Adapter */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.application.SignupAccountStore;
import com.newsverification.signup.application.SignupException;
import com.newsverification.signup.domain.UserAccount;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** users 테이블 기반 미인증 계정 생성과 활성화 */
@Repository
public class JpaSignupAccountStore implements SignupAccountStore {

    private final UserAccountRepository repository;

    public JpaSignupAccountStore(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Account create(NewAccount newAccount) {
        rejectDuplicate(newAccount);
        try {
            UserAccount saved = repository.saveAndFlush(new UserAccount(
                    newAccount.username(),
                    newAccount.passwordHash(),
                    newAccount.email(),
                    newAccount.phoneNumber(),
                    newAccount.inviteCodeVerifiedAt()
            ));
            return toAccount(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new SignupException("DUPLICATE_ACCOUNT");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findById(long userId) {
        return repository.findById(userId).map(this::toAccount);
    }

    @Override
    @Transactional
    public Account activate(long userId, Instant verifiedAt) {
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new SignupException("SIGNUP_NOT_FOUND"));
        account.activate(verifiedAt);
        return toAccount(account);
    }

    /** 가입 처리 실패로 남은 미인증 계정 정리 */
    @Override
    @Transactional
    public void deletePending(long userId) {
        repository.findById(userId)
                .filter(UserAccount::pendingEmail)
                .ifPresent(repository::delete);
    }

    private void rejectDuplicate(NewAccount account) {
        if (repository.existsByUsername(account.username())) {
            throw new SignupException("USERNAME_ALREADY_EXISTS");
        }
        if (repository.existsByEmail(account.email())) {
            throw new SignupException("EMAIL_ALREADY_EXISTS");
        }
        if (repository.existsByPhoneNumber(account.phoneNumber())) {
            throw new SignupException("PHONE_ALREADY_EXISTS");
        }
    }

    private Account toAccount(UserAccount account) {
        return new Account(account.id(), account.username(), account.email(), account.active());
    }
}
