/* 소셜 계정 식별자 Repository */
package com.newsverification.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Provider 식별자 기반 회원 조회 */
public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, Long> {

    Optional<SocialAccountEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
