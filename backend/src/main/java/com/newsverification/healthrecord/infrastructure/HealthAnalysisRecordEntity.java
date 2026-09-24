/* 저장 건강 분석 JPA 모델 */
package com.newsverification.healthrecord.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** 회원이 명시적으로 저장한 건강 분석 결과 */
@Entity
@Table(name = "health_analysis_records")
public class HealthAnalysisRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "publisher_domain_id", nullable = false)
    private Long publisherDomainId;

    @Column(name = "article_url", nullable = false, length = 2048)
    private String articleUrl;

    @Column(name = "normalized_url_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] normalizedUrlDigest;

    @Column(name = "article_title", nullable = false, length = 500)
    private String articleTitle;

    @Column(name = "article_published_at")
    private LocalDateTime articlePublishedAt;

    @Column(name = "article_modified_at")
    private LocalDateTime articleModifiedAt;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false, length = 30)
    private HealthAnalysisResult.OverallStatus overallStatus;

    @Column(name = "total_claim_count", nullable = false)
    private int totalClaimCount;

    @Column(name = "supported_claim_count", nullable = false)
    private int supportedClaimCount;

    @Column(name = "verification_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal verificationRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "expert_review_status", nullable = false, length = 20)
    private HealthAnalysisResult.ExpertReviewStatus expertReviewStatus;

    @Column(name = "expert_reviewed_at")
    private Instant expertReviewedAt;

    @Column(name = "ai_model_version", nullable = false, length = 100)
    private String aiModelVersion;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    @Column(name = "evidence_allowlist_version", nullable = false, length = 100)
    private String evidenceAllowlistVersion;

    @Version
    @Column(nullable = false)
    private long version;

    protected HealthAnalysisRecordEntity() {
    }

    /** 분석 결과와 삭제 예정 시각 구성 */
    HealthAnalysisRecordEntity(
            long userId,
            long publisherDomainId,
            String articleUrl,
            byte[] normalizedUrlDigest,
            String articleTitle,
            LocalDateTime articlePublishedAt,
            LocalDateTime articleModifiedAt,
            Instant analyzedAt,
            Instant expiresAt,
            HealthAnalysisResult.OverallStatus overallStatus,
            int totalClaimCount,
            int supportedClaimCount,
            BigDecimal verificationRate,
            HealthAnalysisResult.ExpertReviewStatus expertReviewStatus,
            String aiModelVersion,
            String policyVersion,
            String evidenceAllowlistVersion
    ) {
        this.userId = userId;
        this.publisherDomainId = publisherDomainId;
        this.articleUrl = articleUrl;
        this.normalizedUrlDigest = normalizedUrlDigest.clone();
        this.articleTitle = articleTitle;
        this.articlePublishedAt = articlePublishedAt;
        this.articleModifiedAt = articleModifiedAt;
        this.analyzedAt = analyzedAt;
        this.expiresAt = expiresAt;
        this.overallStatus = overallStatus;
        this.totalClaimCount = totalClaimCount;
        this.supportedClaimCount = supportedClaimCount;
        this.verificationRate = verificationRate;
        this.expertReviewStatus = expertReviewStatus;
        this.aiModelVersion = aiModelVersion;
        this.policyVersion = policyVersion;
        this.evidenceAllowlistVersion = evidenceAllowlistVersion;
    }

    Long id() {
        return id;
    }

    String articleTitle() {
        return articleTitle;
    }

    HealthAnalysisResult.OverallStatus overallStatus() {
        return overallStatus;
    }

    Instant analyzedAt() {
        return analyzedAt;
    }

    Instant expiresAt() {
        return expiresAt;
    }
}
