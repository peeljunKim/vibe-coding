/* 저장 건강 분석 근거 JPA 모델 */
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

import java.time.LocalDate;

/** 저장 결과에 사용한 공식 기관 또는 PubMed 근거 */
@Entity
@Table(name = "health_evidences")
public class HealthEvidenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "health_analysis_record_id", nullable = false)
    private Long recordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 20)
    private HealthAnalysisResult.EvidenceSourceKind sourceKind;

    @Column(name = "source_identifier", length = 100)
    private String sourceIdentifier;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(name = "provider_name", nullable = false, length = 255)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_type", length = 40)
    private HealthAnalysisResult.EvidenceStudyType studyType;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "source_url", nullable = false, length = 2048)
    private String sourceUrl;

    @Column(name = "source_url_digest", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] sourceUrlDigest;

    protected HealthEvidenceEntity() {
    }

    HealthEvidenceEntity(
            long recordId,
            HealthAnalysisResult.Evidence evidence,
            byte[] sourceUrlDigest
    ) {
        this.recordId = recordId;
        this.sourceKind = evidence.sourceKind();
        this.sourceIdentifier = evidence.sourceIdentifier();
        this.title = evidence.title();
        this.providerName = evidence.provider();
        this.studyType = evidence.studyType();
        this.publicationDate = evidence.publishedOrUpdatedDate();
        this.sourceUrl = evidence.sourceUrl().toASCIIString();
        this.sourceUrlDigest = sourceUrlDigest.clone();
    }

    Long id() {
        return id;
    }
}
