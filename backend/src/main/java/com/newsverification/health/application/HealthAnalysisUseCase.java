/* 건강 분석 분야 판별 분기 */
package com.newsverification.health.application;

import java.time.Instant;
import java.util.Optional;

/** 건강 기사만 후속 분석으로 전달하는 흐름 */
public class HealthAnalysisUseCase {

    private final HealthArticleScreeningService screeningService;
    private final HealthAnalysisPort analysisPort;
    private final HealthTopicFailureUsagePolicy usagePolicy;

    /** 분야 판별과 후속 분석 경계 구성 */
    public HealthAnalysisUseCase(
            HealthArticleScreeningService screeningService,
            HealthAnalysisPort analysisPort,
            HealthTopicFailureUsagePolicy usagePolicy
    ) {
        this.screeningService = screeningService;
        this.analysisPort = analysisPort;
        this.usagePolicy = usagePolicy;
    }

    /** 기사 수집부터 후속 건강 분석 시작까지의 분기 */
    public HealthAnalysisRoutingResult start(
            String rawUrl,
            HealthAnalysisUsageSubject usageSubject
    ) {
        HealthArticleScreeningResult screeningResult = screen(rawUrl, usageSubject);
        if (screeningResult.decision() == HealthArticleTopicDecision.HEALTH_RELATED) {
            usagePolicy.recordAnalysisStart(usageSubject);
        }
        return continueAfterScreening(screeningResult, usageSubject, Instant.MAX);
    }

    /** 접수 한도 확인과 기사 안전 수집·분야 판별 */
    public HealthArticleScreeningResult screen(
            String rawUrl,
            HealthAnalysisUsageSubject usageSubject
    ) {
        usagePolicy.verifyCanStart(usageSubject);
        return screeningService.screen(rawUrl);
    }

    /** 분야 판별 이후 후속 분석 또는 중단 */
    public HealthAnalysisRoutingResult continueAfterScreening(
            HealthArticleScreeningResult screeningResult,
            HealthAnalysisUsageSubject usageSubject,
            Instant deadlineAt
    ) {
        return switch (screeningResult.decision()) {
            case HEALTH_RELATED -> {
                yield new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(analysisPort.analyze(screeningResult.article(), deadlineAt))
                );
            }
            case NOT_HEALTH_RELATED -> stopAfterTopicFailure(
                    usageSubject,
                    HealthAnalysisRoutingStatus.NOT_HEALTH_ARTICLE,
                    "건강·의학 기사만 확인할 수 있습니다."
            );
            case UNCERTAIN -> stopAfterTopicFailure(
                    usageSubject,
                    HealthAnalysisRoutingStatus.TOPIC_UNCERTAIN,
                    "건강·의학 기사 여부를 확인하기 어렵습니다."
            );
        };
    }

    /** 분야 판별 실패 이용량 기록과 사용자 안내 */
    private HealthAnalysisRoutingResult stopAfterTopicFailure(
            HealthAnalysisUsageSubject usageSubject,
            HealthAnalysisRoutingStatus status,
            String userMessage
    ) {
        usagePolicy.recordFailure(usageSubject);
        return new HealthAnalysisRoutingResult(status, Optional.of(userMessage));
    }
}
