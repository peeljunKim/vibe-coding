/* 건강 분석 분야 판별 분기 */
package com.newsverification.health.application;

import java.util.Optional;

/** 건강 기사만 후속 분석으로 전달하는 흐름 */
public class HealthAnalysisUseCase {

    private final HealthArticleScreeningService screeningService;
    private final HealthAnalysisPort analysisPort;

    /** 분야 판별과 후속 분석 경계 구성 */
    public HealthAnalysisUseCase(
            HealthArticleScreeningService screeningService,
            HealthAnalysisPort analysisPort
    ) {
        this.screeningService = screeningService;
        this.analysisPort = analysisPort;
    }

    /** 기사 수집부터 후속 건강 분석 시작까지의 분기 */
    public HealthAnalysisRoutingResult start(String rawUrl) {
        HealthArticleScreeningResult screeningResult = screeningService.screen(rawUrl);
        return switch (screeningResult.decision()) {
            case HEALTH_RELATED -> {
                analysisPort.start(screeningResult.article());
                yield new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty()
                );
            }
            case NOT_HEALTH_RELATED -> new HealthAnalysisRoutingResult(
                    HealthAnalysisRoutingStatus.NOT_HEALTH_ARTICLE,
                    Optional.of("건강·의학 기사만 확인할 수 있습니다.")
            );
            case UNCERTAIN -> new HealthAnalysisRoutingResult(
                    HealthAnalysisRoutingStatus.TOPIC_UNCERTAIN,
                    Optional.of("건강·의학 기사 여부를 확인하기 어렵습니다.")
            );
        };
    }
}
