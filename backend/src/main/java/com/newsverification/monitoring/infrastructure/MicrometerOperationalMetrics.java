/* Micrometer 업무 Metric Adapter */
package com.newsverification.monitoring.infrastructure;

import com.newsverification.monitoring.application.OperationalMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** 고정 Enum Label 기반 Prometheus Metric 기록 */
@Component
public class MicrometerOperationalMetrics implements OperationalMetrics {

    private final MeterRegistry registry;

    public MicrometerOperationalMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        registerBoundedSeries();
    }

    @Override
    public void recordAnalysisRequest(Feature feature, RequestOutcome outcome) {
        Counter.builder("news.verification.analysis.requests")
                .tag("feature", label(feature))
                .tag("outcome", label(outcome))
                .register(registry)
                .increment();
    }

    @Override
    public void recordWorkerResult(Feature feature, WorkerOutcome outcome) {
        Counter.builder("news.verification.analysis.worker.results")
                .tag("feature", label(feature))
                .tag("outcome", label(outcome))
                .register(registry)
                .increment();
    }

    @Override
    public void recordWorkerDuration(Feature feature, Duration duration) {
        Timer.builder("news.verification.analysis.worker.duration")
                .tag("feature", label(feature))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    @Override
    public void recordCacheLookup(Feature feature, CacheOutcome outcome) {
        Counter.builder("news.verification.analysis.cache.lookups")
                .tag("feature", label(feature))
                .tag("outcome", label(outcome))
                .register(registry)
                .increment();
    }

    @Override
    public void recordArticleExtraction(ExtractionOutcome outcome, ExtractionFailure failure) {
        Counter.builder("news.verification.article.extractions")
                .tag("outcome", label(outcome))
                .tag("failure", label(failure))
                .register(registry)
                .increment();
    }

    private String label(Enum<?> value) {
        return Objects.requireNonNull(value).name().toLowerCase(Locale.ROOT);
    }

    /** 기동 직후 Dashboard Query가 확인 가능한 고정 Series 등록 */
    private void registerBoundedSeries() {
        for (Feature feature : Feature.values()) {
            for (RequestOutcome outcome : RequestOutcome.values()) {
                Counter.builder("news.verification.analysis.requests")
                        .tag("feature", label(feature))
                        .tag("outcome", label(outcome))
                        .register(registry);
            }
            for (WorkerOutcome outcome : WorkerOutcome.values()) {
                Counter.builder("news.verification.analysis.worker.results")
                        .tag("feature", label(feature))
                        .tag("outcome", label(outcome))
                        .register(registry);
            }
            Timer.builder("news.verification.analysis.worker.duration")
                    .tag("feature", label(feature))
                    .publishPercentileHistogram()
                    .register(registry);
            for (CacheOutcome outcome : CacheOutcome.values()) {
                Counter.builder("news.verification.analysis.cache.lookups")
                        .tag("feature", label(feature))
                        .tag("outcome", label(outcome))
                        .register(registry);
            }
        }
        Counter.builder("news.verification.article.extractions")
                .tag("outcome", label(ExtractionOutcome.SUCCEEDED))
                .tag("failure", label(ExtractionFailure.NONE))
                .register(registry);
        for (ExtractionFailure failure : ExtractionFailure.values()) {
            if (failure != ExtractionFailure.NONE) {
                Counter.builder("news.verification.article.extractions")
                        .tag("outcome", label(ExtractionOutcome.FAILED))
                        .tag("failure", label(failure))
                        .register(registry);
            }
        }
    }
}
