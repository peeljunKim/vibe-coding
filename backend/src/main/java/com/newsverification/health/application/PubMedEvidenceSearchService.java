/* PubMed 근거 검색 결과 제한 */
package com.newsverification.health.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

/** 외부 PubMed 후보를 건강 분석용 근거로 검증하는 Service */
public final class PubMedEvidenceSearchService {

    private final PubMedEvidenceSearchPort searchPort;
    private final HealthEvidenceLinkChecker linkChecker;

    /** 검색 Port와 근거 링크 검증 경계 구성 */
    public PubMedEvidenceSearchService(
            PubMedEvidenceSearchPort searchPort,
            HealthEvidenceLinkChecker linkChecker
    ) {
        this.searchPort = Objects.requireNonNull(searchPort);
        this.linkChecker = Objects.requireNonNull(linkChecker);
    }

    /** 확인된 건강 주장 기반 근거 검색 */
    public PubMedEvidenceSearchPort.SearchResponse search(
            PubMedEvidenceSearchPort.SearchRequest request
    ) {
        PubMedEvidenceSearchPort.SearchResponse response = searchPort.search(request);
        if (response.status() != PubMedEvidenceSearchPort.SearchStatus.COMPLETED) {
            return response;
        }
        var evidenceByPmid = new LinkedHashMap<String, PubMedEvidenceSearchPort.Evidence>();
        response.evidences().forEach(evidence -> evidenceByPmid.putIfAbsent(evidence.pmid(), evidence));
        var allowedEvidence = new ArrayList<PubMedEvidenceSearchPort.Evidence>();
        for (PubMedEvidenceSearchPort.Evidence evidence : evidenceByPmid.values()) {
            HealthEvidenceLinkChecker.Status linkStatus = linkChecker.check(evidence.sourceUrl());
            if (linkStatus == HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE) {
                return PubMedEvidenceSearchPort.SearchResponse.temporaryFailure();
            }
            if (linkStatus == HealthEvidenceLinkChecker.Status.AVAILABLE) {
                allowedEvidence.add(evidence);
                if (allowedEvidence.size()
                        == request.maxResultsPerClaim() * request.claims().size()) {
                    break;
                }
            }
        }
        return allowedEvidence.isEmpty()
                ? PubMedEvidenceSearchPort.SearchResponse.noResults()
                : PubMedEvidenceSearchPort.SearchResponse.completed(allowedEvidence);
    }
}
