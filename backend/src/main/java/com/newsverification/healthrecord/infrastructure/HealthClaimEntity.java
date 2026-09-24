/* 저장 건강 분석 주장 JPA 모델 */
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

/** 저장 결과의 핵심 주장 */
@Entity
@Table(name = "health_claims")
public class HealthClaimEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "health_analysis_record_id", nullable = false)
    private Long recordId;

    @Column(name = "claim_order", nullable = false)
    private int claimOrder;

    @Column(name = "claim_text", nullable = false, columnDefinition = "TEXT")
    private String claimText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HealthAnalysisResult.ClaimStatus status;

    @Column(name = "easy_reason", nullable = false, columnDefinition = "TEXT")
    private String easyReason;

    protected HealthClaimEntity() {
    }

    HealthClaimEntity(long recordId, HealthAnalysisResult.Claim claim) {
        this.recordId = recordId;
        this.claimOrder = claim.order();
        this.claimText = claim.claim();
        this.status = claim.status();
        this.easyReason = claim.reason();
    }

    Long id() {
        return id;
    }
}
