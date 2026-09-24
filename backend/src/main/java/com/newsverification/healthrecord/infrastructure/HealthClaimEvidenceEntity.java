/* 주장과 근거 관계 JPA 모델 */
package com.newsverification.healthrecord.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** 저장 주장과 근거의 표시 관계 */
@Entity
@Table(name = "health_claim_evidences")
public class HealthClaimEvidenceEntity {

    @EmbeddedId
    private HealthClaimEvidenceId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private HealthAnalysisResult.EvidenceRelationType relationType;

    @Column(name = "evidence_order", nullable = false)
    private int evidenceOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "conflict_note", columnDefinition = "TEXT")
    private String conflictNote;

    protected HealthClaimEvidenceEntity() {
    }

    HealthClaimEvidenceEntity(
            long claimId,
            long evidenceId,
            int evidenceOrder,
            HealthAnalysisResult.Evidence evidence
    ) {
        this.id = new HealthClaimEvidenceId(claimId, evidenceId);
        this.relationType = evidence.relationType();
        this.evidenceOrder = evidenceOrder;
        this.summary = evidence.summary();
        this.conflictNote = evidence.conflictDescription();
    }
}
