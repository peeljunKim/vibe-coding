/* 업무 Metric 이름과 제한된 Label 검증 */
package com.newsverification.monitoring.infrastructure;

import com.newsverification.monitoring.application.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 분석·Cache·기사 수집 Metric 계약 */
class MicrometerOperationalMetricsTest {

    /** 고정 Label별 Counter와 Timer 기록 */
    @Test
    void recordsBoundedOperationalMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new MicrometerOperationalMetrics(registry);

        metrics.recordAnalysisRequest(
                OperationalMetrics.Feature.HEALTH,
                OperationalMetrics.RequestOutcome.ACCEPTED
        );
        metrics.recordAnalysisRequest(
                OperationalMetrics.Feature.HEALTH,
                OperationalMetrics.RequestOutcome.QUEUE_FULL
        );
        metrics.recordAnalysisRequest(
                OperationalMetrics.Feature.HEADLINE,
                OperationalMetrics.RequestOutcome.REDIS_UNAVAILABLE
        );
        metrics.recordWorkerResult(
                OperationalMetrics.Feature.HEALTH,
                OperationalMetrics.WorkerOutcome.COMPLETED
        );
        metrics.recordWorkerDuration(OperationalMetrics.Feature.HEALTH, Duration.ofMillis(250));
        metrics.recordCacheLookup(
                OperationalMetrics.Feature.HEADLINE,
                OperationalMetrics.CacheOutcome.HIT
        );
        metrics.recordCacheLookup(
                OperationalMetrics.Feature.HEALTH,
                OperationalMetrics.CacheOutcome.MISS
        );
        metrics.recordArticleExtraction(
                OperationalMetrics.ExtractionOutcome.SUCCEEDED,
                OperationalMetrics.ExtractionFailure.NONE
        );
        metrics.recordArticleExtraction(
                OperationalMetrics.ExtractionOutcome.FAILED,
                OperationalMetrics.ExtractionFailure.UNSUPPORTED_PUBLISHER
        );

        assertThat(registry.get("news.verification.analysis.requests")
                .tags("feature", "health", "outcome", "accepted")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.worker.results")
                .tags("feature", "health", "outcome", "completed")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.requests")
                .tags("feature", "health", "outcome", "queue_full")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.requests")
                .tags("feature", "headline", "outcome", "redis_unavailable")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.worker.duration")
                .tag("feature", "health").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(250.0);
        assertThat(registry.get("news.verification.analysis.cache.lookups")
                .tags("feature", "headline", "outcome", "hit")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.cache.lookups")
                .tags("feature", "health", "outcome", "miss")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.article.extractions")
                .tags("outcome", "succeeded", "failure", "none")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.article.extractions")
                .tags("outcome", "failed", "failure", "unsupported_publisher")
                .counter().count()).isEqualTo(1.0);
    }
}
