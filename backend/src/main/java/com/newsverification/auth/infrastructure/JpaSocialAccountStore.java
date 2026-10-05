/* JPA 소셜 계정 Adapter */
package com.newsverification.auth.infrastructure;

import com.newsverification.auth.application.SocialAccountStore;
import com.newsverification.auth.application.SocialLoginException;
import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.infrastructure.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 소셜 전용 계정과 Provider 식별자의 원자 저장 */
@Repository
public class JpaSocialAccountStore implements SocialAccountStore {

    private final UserAccountRepository userAccountRepository;
    private final SocialAccountRepository socialAccountRepository;

    public JpaSocialAccountStore(
            UserAccountRepository userAccountRepository,
            SocialAccountRepository socialAccountRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findByProviderAndSubject(String provider, String subject) {
        return socialAccountRepository.findByProviderAndProviderSubject(provider, subject)
                .map(SocialAccountEntity::userAccount)
                .map(this::toAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userAccountRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public Account create(NewAccount newAccount) {
        if (userAccountRepository.existsByEmail(newAccount.email())) {
            throw new SocialLoginException("SOCIAL_EMAIL_ALREADY_REGISTERED");
        }

        try {
            UserAccount account = userAccountRepository.saveAndFlush(
                    UserAccount.social(newAccount.email(), newAccount.verifiedAt())
            );
            socialAccountRepository.saveAndFlush(new SocialAccountEntity(
                    account,
                    newAccount.provider(),
                    newAccount.subject(),
                    newAccount.email()
            ));
            return toAccount(account);
        } catch (DataIntegrityViolationException exception) {
            throw new SocialLoginException("SOCIAL_ACCOUNT_CONFLICT");
        }
    }

    private Account toAccount(UserAccount account) {
        return new Account(account.id(), account.role(), account.active());
    }
}
