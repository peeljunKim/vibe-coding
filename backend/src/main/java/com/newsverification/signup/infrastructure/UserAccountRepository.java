/* 일반 회원 계정 Repository */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

/** 가입 중복 검사와 계정 조회 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
