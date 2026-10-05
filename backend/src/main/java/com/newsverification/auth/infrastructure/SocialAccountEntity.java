/* 소셜 계정 식별자 영속 모델 */
package com.newsverification.auth.infrastructure;

import com.newsverification.signup.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Provider 식별자와 내부 회원 연결 */
@Entity
@Table(name = "user_social_accounts")
public class SocialAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "provider_email", nullable = false, length = 320)
    private String providerEmail;

    @Column(name = "is_signup_identity", nullable = false)
    private boolean signupIdentity;

    protected SocialAccountEntity() {
    }

    public SocialAccountEntity(
            UserAccount userAccount,
            String provider,
            String providerSubject,
            String providerEmail
    ) {
        this.userAccount = userAccount;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
        this.signupIdentity = true;
    }

    public UserAccount userAccount() {
        return userAccount;
    }
}
