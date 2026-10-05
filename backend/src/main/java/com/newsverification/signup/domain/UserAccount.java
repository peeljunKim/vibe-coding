/* 회원 계정 영속 모델 */
package com.newsverification.signup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

/** 일반·소셜 회원의 인증 상태 */
@Entity
@Table(name = "users")
public class UserAccount {

    private static final Duration WITHDRAWAL_RECOVERY_PERIOD = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(nullable = false, length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 20)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "phone_number", length = 11)
    private String phoneNumber;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "invite_code_verified_at", nullable = false)
    private Instant inviteCodeVerifiedAt;

    @Column(name = "failed_login_count", insertable = false, nullable = false)
    private int failedLoginCount;

    @Column(name = "login_locked_until")
    private Instant loginLockedUntil;

    @Column(name = "withdrawal_requested_at")
    private Instant withdrawalRequestedAt;

    @Column(name = "scheduled_deletion_at")
    private Instant scheduledDeletionAt;

    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false, nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserAccount() {
    }

    public UserAccount(
            String username,
            String passwordHash,
            String email,
            String phoneNumber,
            Instant inviteCodeVerifiedAt
    ) {
        this.accountType = "LOCAL";
        this.role = "USER";
        this.status = UserStatus.PENDING_EMAIL;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.inviteCodeVerifiedAt = inviteCodeVerifiedAt;
    }

    /** 소셜 전용 활성 계정 생성 */
    public static UserAccount social(String email, Instant verifiedAt) {
        UserAccount account = new UserAccount();
        account.accountType = "SOCIAL";
        account.role = "USER";
        account.status = UserStatus.ACTIVE;
        account.email = Objects.requireNonNull(email);
        account.emailVerifiedAt = Objects.requireNonNull(verifiedAt);
        account.inviteCodeVerifiedAt = verifiedAt;
        return account;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public boolean active() {
        return status == UserStatus.ACTIVE;
    }

    public boolean pendingEmail() {
        return status == UserStatus.PENDING_EMAIL;
    }

    public boolean withdrawalPending() {
        return status == UserStatus.WITHDRAWAL_PENDING;
    }

    public boolean localAccount() {
        return "LOCAL".equals(accountType);
    }

    public String role() {
        return role;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int failedLoginCount() {
        return failedLoginCount;
    }

    public Instant loginLockedUntil() {
        return loginLockedUntil;
    }

    public Instant scheduledDeletionAt() {
        return scheduledDeletionAt;
    }

    public Instant withdrawalRecoveryDeadline() {
        return withdrawalRequestedAt == null
                ? null
                : withdrawalRequestedAt.plus(WITHDRAWAL_RECOVERY_PERIOD);
    }

    /** 로그인 잠금 상태 확인 */
    public boolean loginLockedAt(Instant now) {
        return loginLockedUntil != null && now.isBefore(loginLockedUntil);
    }

    /** 만료된 로그인 잠금 초기화 */
    public void clearExpiredLoginLock(Instant now) {
        if (loginLockedUntil != null && !now.isBefore(loginLockedUntil)) {
            resetLoginFailures();
        }
    }

    /** 로그인 실패 누적과 잠금 전환 */
    public boolean recordLoginFailure(Instant now, int maximumFailures, long lockSeconds) {
        failedLoginCount = Math.min(failedLoginCount + 1, maximumFailures);
        if (failedLoginCount >= maximumFailures) {
            loginLockedUntil = now.plusSeconds(lockSeconds);
            return true;
        }
        return false;
    }

    /** 로그인 실패 상태 초기화 */
    public void resetLoginFailures() {
        failedLoginCount = 0;
        loginLockedUntil = null;
    }

    /** 비밀번호 변경과 로그인 실패 상태 초기화 */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
        resetLoginFailures();
    }

    /** 이메일 인증 완료 상태 전환 */
    public void activate(Instant verifiedAt) {
        if (status == UserStatus.PENDING_EMAIL) {
            emailVerifiedAt = verifiedAt;
            status = UserStatus.ACTIVE;
        }
    }

    /** 탈퇴와 물리 삭제 대기 상태 전환 */
    public void requestWithdrawal(Instant requestedAt, Instant deletionAt) {
        if (status != UserStatus.ACTIVE || !deletionAt.isAfter(requestedAt)) {
            throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
        }
        status = UserStatus.WITHDRAWAL_PENDING;
        withdrawalRequestedAt = Objects.requireNonNull(requestedAt);
        scheduledDeletionAt = Objects.requireNonNull(deletionAt);
    }

    /** 7일 이내 탈퇴 취소 */
    public boolean recoverWithdrawal(Instant now) {
        Instant recoveryDeadline = withdrawalRecoveryDeadline();
        if (!withdrawalPending()
                || recoveryDeadline == null
                || !now.isBefore(recoveryDeadline)) {
            return false;
        }
        status = UserStatus.ACTIVE;
        withdrawalRequestedAt = null;
        scheduledDeletionAt = null;
        resetLoginFailures();
        return true;
    }

    public enum UserStatus {
        PENDING_EMAIL,
        ACTIVE,
        WITHDRAWAL_PENDING
    }
}
