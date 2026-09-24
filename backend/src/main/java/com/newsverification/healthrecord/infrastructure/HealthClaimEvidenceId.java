/* 주장과 근거 복합 식별자 */
package com.newsverification.healthrecord.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** 주장·근거 다대다 관계 키 */
@Embeddable
public class HealthClaimEvidenceId implements Serializable {

    @Column(name = "health_claim_id", nullable = false)
    private Long claimId;

    @Column(name = "health_evidence_id", nullable = false)
    private Long evidenceId;

    protected HealthClaimEvidenceId() {
    }

    HealthClaimEvidenceId(long claimId, long evidenceId) {
        this.claimId = claimId;
        this.evidenceId = evidenceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HealthClaimEvidenceId that)) {
            return false;
        }
        return Objects.equals(claimId, that.claimId)
                && Objects.equals(evidenceId, that.evidenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimId, evidenceId);
    }
}
