/* 일반 회원 계정 Repository */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.domain.UserAccount.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/** 가입 중복 검사와 계정 조회 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsernameOrEmailOrPhoneNumber(String username, String email, String phoneNumber);

    int deleteByStatusAndCreatedAtBefore(UserStatus status, Instant cutoff);
}
