/* 일반 회원 계정 Repository */
package com.newsverification.signup.infrastructure;

import com.newsverification.signup.domain.UserAccount;
import com.newsverification.signup.domain.UserAccount.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 가입 중복 검사와 계정 조회 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsernameOrEmailOrPhoneNumber(String username, String email, String phoneNumber);

    int deleteByStatusAndCreatedAtBefore(UserStatus status, Instant cutoff);

    /** 로그인 실패 횟수의 동시 갱신 보호 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserAccount account where account.username = :username")
    Optional<UserAccount> findByUsernameForLogin(@Param("username") String username);

    Optional<UserAccount> findByEmailAndAccountTypeAndStatus(
            String email,
            String accountType,
            UserStatus status
    );

    /** 비밀번호 변경의 동시 갱신 보호 조회 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account from UserAccount account
            where account.id = :userId
              and account.accountType = :accountType
              and account.status = :status
            """)
    Optional<UserAccount> findActiveLocalByIdForUpdate(
            @Param("userId") long userId,
            @Param("accountType") String accountType,
            @Param("status") UserStatus status
    );
}
