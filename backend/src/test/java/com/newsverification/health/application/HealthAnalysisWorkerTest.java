/* 건강 분석 Background Worker 검증 */
package com.newsverification.health.application;

import com.newsverification.analysis.application.AnalysisJobLifecycleService;
import com.newsverification.analysis.application.AnalysisJobOutcome;
import com.newsverification.analysis.application.AnalysisJobOutcomeStore;
import com.newsverification.analysis.application.AnalysisJobStore;
import com.newsverification.analysis.domain.AnalysisJob;
import com.newsverification.analysis.domain.AnalysisJobOwner;
import com.newsverification.analysis.domain.AnalysisJobOwnerType;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.analysiscache.application.AnalysisCacheKey;
import com.newsverification.analysiscache.application.AnalysisCacheKeyFactory;
import com.newsverification.analysiscache.application.AnalysisCacheVersions;
import com.newsverification.analysiscache.application.AnalysisCacheViewer;
import com.newsverification.analysiscache.application.ArticleRevisionFingerprint;
import com.newsverification.analysiscache.application.CachedAnalysisResult;
import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.monitoring.application.OperationalMetrics;
import com.newsverification.monitoring.infrastructure.MicrometerOperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 단일 소비·단계 전환·완료·실패·Deadline 검증 */
class HealthAnalysisWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T01:00:00Z");
    private static final AnalysisJobOwner OWNER = new AnalysisJobOwner(
            AnalysisJobOwnerType.MEMBER,
            "member-owner-key"
    );

    /** 안전 수집과 분야 판별 이후 Mock 결과 완료 */
    @Test
    void completesQueuedHealthAnalysisOnce() {
        var store = new InMemoryJobStore();
        AnalysisJobLifecycleService lifecycle = lifecycle(store, NOW);
        AnalysisJob accepted = lifecycle.accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        var queue = new SingleTaskQueue(task, true);
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        ExtractedArticle article = article();
        HealthAnalysisResult result = result(article);
        HealthArticleScreeningResult screening = new HealthArticleScreeningResult(
                article,
                HealthArticleTopicDecision.HEALTH_RELATED
        );
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenReturn(screening);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(true, 1, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenReturn(new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(result)
                ));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new MicrometerOperationalMetrics(registry);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                queue,
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                HealthEvidenceLinkValidationService.trustAll(),
                new ObjectMapper(),
                clock,
                "test-worker",
                metrics
        );

        assertThat(worker.runOnce()).isTrue();

        AnalysisJob completed = store.findById(accepted.id()).orElseThrow();
        assertThat(completed.status()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().type())
                .isEqualTo(AnalysisJobOutcome.Type.RESULT);
        assertThat(queue.deliveryCount).isEqualTo(1);
        assertThat(worker.runOnce()).isFalse();
        verify(useCase).screen(task.articleUrl(), task.usageSubject());
        verify(usagePolicy).recordAnalysisStart(task.usageSubject());
        verify(cache).saveHealth(
                AnalysisCacheKeyFactory.health(task.articleUrl(), versions).orElseThrow(),
                result,
                ArticleRevisionFingerprint.from(article),
                AnalysisCacheViewer.fingerprint(
                        task.userType().name(),
                        task.usageIdentifierKeys()
                )
        );
        assertThat(registry.get("news.verification.analysis.cache.lookups")
                .tags("feature", "health", "outcome", "miss")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.worker.results")
                .tags("feature", "health", "outcome", "completed")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.get("news.verification.analysis.worker.duration")
                .tag("feature", "health").timer().count()).isEqualTo(1L);
    }

    /** 완료 상태 저장 거부 시 공용 Cache 게시 차단 */
    @Test
    void doesNotPublishCacheWhenCompletionWriteIsRejected() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-rejected", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        HealthArticleScreeningResult screening = screeningResult();
        HealthAnalysisResult result = result(screening.article());
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenReturn(screening);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(true, 1, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenReturn(new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(result)
                ));
        store.rejectOutcomeWrites = true;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.PROCESSING);
        assertThat(store.findOutcome(accepted.id())).isEmpty();
        verify(cache, never()).saveHealth(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    /** 일시 근거 오류 재확인 뒤 Cache Hit의 분석 Port 미호출 */
    @Test
    void completesFromCacheAfterMatchingArticleRevision() {
        var store = new InMemoryJobStore();
        AnalysisJobLifecycleService lifecycle = lifecycle(store, NOW);
        AnalysisJob accepted = lifecycle.accept("analysis-cached", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        String viewerFingerprint = AnalysisCacheViewer.fingerprint(
                task.userType().name(),
                task.usageIdentifierKeys()
        );
        ExtractedArticle currentArticle = article();
        URI temporaryUrl = URI.create("https://evidence.example/temporary");
        HealthAnalysisResult cachedResult = resultWithEvidence(
                currentArticle,
                temporaryUrl,
                HealthAnalysisResult.ClaimStatus.SUPPORTED
        );
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                cachedResult,
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(currentArticle)
        )));
        when(useCase.read(task.articleUrl())).thenReturn(currentArticle);
        when(usagePolicy.recordCacheAccess(
                task.usageSubject(), cacheKey, viewerFingerprint
        )).thenReturn(Optional.of(new HealthTopicFailureUsageResult(false, 1, 5)));
        AtomicInteger evidenceChecks = new AtomicInteger();
        var validator = new HealthEvidenceLinkValidationService(sourceUrl -> {
            evidenceChecks.incrementAndGet();
            return HealthEvidenceLinkChecker.Status.TEMPORARY_FAILURE;
        });
        Clock clock = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                validator,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        assertThat(outcome.type()).isEqualTo(AnalysisJobOutcome.Type.RESULT);
        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.COMPLETED);
        verify(useCase).read(task.articleUrl());
        verify(useCase, never()).screen(task.articleUrl(), task.usageSubject());
        verify(useCase, never()).continueAfterScreening(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
        verify(cache, never()).evictHealth(cacheKey);
        assertThat(evidenceChecks).hasValue(2);
    }

    /** 사라진 근거의 무차감 자동 재분석과 Cache 교체 */
    @Test
    void reanalyzesWithoutChargeWhenCachedEvidenceIsMissing() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-evidence-refresh", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        ExtractedArticle currentArticle = article();
        URI missingUrl = URI.create("https://evidence.example/missing");
        HealthAnalysisResult cachedResult = resultWithEvidence(
                currentArticle,
                missingUrl,
                HealthAnalysisResult.ClaimStatus.SUPPORTED
        );
        HealthAnalysisResult refreshedResult = resultWithEvidence(
                currentArticle,
                URI.create("https://evidence.example/refreshed"),
                HealthAnalysisResult.ClaimStatus.SUPPORTED
        );
        HealthArticleScreeningResult screening = new HealthArticleScreeningResult(
                currentArticle,
                HealthArticleTopicDecision.HEALTH_RELATED
        );
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                cachedResult,
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(currentArticle)
        )));
        when(useCase.read(task.articleUrl())).thenReturn(currentArticle);
        when(useCase.screenForMaintenance(currentArticle)).thenReturn(screening);
        when(usagePolicy.currentUsage(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(false, 1, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenReturn(new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(refreshedResult)
                ));
        var validator = new HealthEvidenceLinkValidationService(
                sourceUrl -> sourceUrl.equals(missingUrl)
                        ? HealthEvidenceLinkChecker.Status.MISSING
                        : HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                validator,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.COMPLETED);
        verify(cache).evictHealth(cacheKey);
        verify(usagePolicy).currentUsage(task.usageSubject());
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
        verify(cache).saveHealth(
                cacheKey,
                refreshedResult,
                ArticleRevisionFingerprint.from(currentArticle),
                AnalysisCacheViewer.fingerprint(
                        task.userType().name(),
                        task.usageIdentifierKeys()
                )
        );
    }

    /** 자동 재분석 실패 시 정상 근거만 포함한 제한 결과 완료 */
    @Test
    void completesLimitedResultWhenAutomaticEvidenceRefreshFails() throws Exception {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-evidence-fallback", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        ExtractedArticle currentArticle = article();
        URI availableUrl = URI.create("https://evidence.example/available");
        URI missingUrl = URI.create("https://evidence.example/missing");
        HealthAnalysisResult cachedResult = resultWithTwoEvidenceClaims(
                currentArticle,
                availableUrl,
                missingUrl
        );
        HealthArticleScreeningResult screening = new HealthArticleScreeningResult(
                currentArticle,
                HealthArticleTopicDecision.HEALTH_RELATED
        );
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                cachedResult,
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(currentArticle)
        )));
        when(useCase.read(task.articleUrl())).thenReturn(currentArticle);
        when(useCase.screenForMaintenance(currentArticle)).thenReturn(screening);
        when(usagePolicy.currentUsage(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(false, 2, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenThrow(new IllegalStateException("mock analysis failure"));
        var validator = new HealthEvidenceLinkValidationService(
                sourceUrl -> sourceUrl.equals(missingUrl)
                        ? HealthEvidenceLinkChecker.Status.MISSING
                        : HealthEvidenceLinkChecker.Status.AVAILABLE
        );
        HealthAnalysisResult expectedLimited = validator.validate(cachedResult)
                .limitedResult()
                .orElseThrow();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                validator,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        HealthAnalysisResult limited = new ObjectMapper().readValue(
                outcome.resultJson(),
                HealthAnalysisResult.class
        );
        assertThat(outcome.type()).isEqualTo(AnalysisJobOutcome.Type.RESULT);
        assertThat(limited.limitedEvidence()).isTrue();
        assertThat(limited.claims()).extracting(HealthAnalysisResult.Claim::order)
                .containsExactly(1);
        assertThat(limited.confirmationRate()).isEqualByComparingTo("100.00");
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
        verify(cache).saveHealth(
                cacheKey,
                expectedLimited,
                ArticleRevisionFingerprint.from(currentArticle),
                AnalysisCacheViewer.fingerprint(
                        task.userType().name(),
                        task.usageIdentifierKeys()
                )
        );
    }

    /** 변경된 기사의 Cache 결과와 이용량 사용 차단 */
    @Test
    void rejectsChangedArticleBeforeCacheUsage() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-changed", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        ExtractedArticle cachedArticle = article();
        ExtractedArticle changedArticle = new ExtractedArticle(
                cachedArticle.sourceUrl(),
                cachedArticle.title(),
                cachedArticle.body() + "\n추가된 문단",
                cachedArticle.publishedAt(),
                cachedArticle.modifiedAt()
        );
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                result(cachedArticle),
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(cachedArticle)
        )));
        when(useCase.read(task.articleUrl())).thenReturn(changedArticle);
        Clock clock = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        assertThat(outcome.errorCode()).isEqualTo("ARTICLE_CHANGED");
        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        verify(useCase).read(task.articleUrl());
        verify(usagePolicy, never()).recordCacheAccess(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(useCase, never()).screen(task.articleUrl(), task.usageSubject());
        verify(useCase, never()).continueAfterScreening(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    /** 사용자 동의 뒤 변경 기사 재분석과 Cache 교체 */
    @Test
    void reanalyzesChangedArticleAfterUserConfirmation() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-reanalyze", OWNER);
        HealthAnalysisTask task = new HealthAnalysisTask(
                accepted.id(),
                "https://news.example/article",
                HealthAnalysisUserType.MEMBER,
                List.of("member-usage-key"),
                true
        );
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        ExtractedArticle cachedArticle = article();
        ExtractedArticle changedArticle = new ExtractedArticle(
                cachedArticle.sourceUrl(),
                cachedArticle.title(),
                cachedArticle.body() + "\n추가된 문단",
                cachedArticle.publishedAt(),
                cachedArticle.modifiedAt()
        );
        HealthArticleScreeningResult screening = new HealthArticleScreeningResult(
                changedArticle,
                HealthArticleTopicDecision.HEALTH_RELATED
        );
        HealthAnalysisResult refreshedResult = result(changedArticle);
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                result(cachedArticle),
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(cachedArticle)
        )));
        when(useCase.read(task.articleUrl())).thenReturn(changedArticle);
        when(useCase.screen(changedArticle, task.usageSubject())).thenReturn(screening);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(true, 1, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenReturn(new HealthAnalysisRoutingResult(
                        HealthAnalysisRoutingStatus.ANALYSIS_STARTED,
                        Optional.empty(),
                        Optional.of(refreshedResult)
                ));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.COMPLETED);
        verify(usagePolicy, never()).recordCacheAccess(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(usagePolicy).recordAnalysisStart(task.usageSubject());
        verify(useCase).read(task.articleUrl());
        verify(useCase, never()).screen(task.articleUrl(), task.usageSubject());
        verify(cache).saveHealth(
                cacheKey,
                refreshedResult,
                ArticleRevisionFingerprint.from(changedArticle),
                AnalysisCacheViewer.fingerprint(
                        task.userType().name(),
                        task.usageIdentifierKeys()
                )
        );
    }

    /** Cache 이용량 차감 후 완료 실패의 차감 결과 보존 */
    @Test
    void preservesCacheChargeWhenCompletionFails() throws Exception {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-cache-failed", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthAnalysisResultCache cache = mock(HealthAnalysisResultCache.class);
        AnalysisCacheVersions versions = AnalysisCacheVersions.mockDefaults();
        AnalysisCacheKey cacheKey = AnalysisCacheKeyFactory.health(task.articleUrl(), versions)
                .orElseThrow();
        String viewerFingerprint = AnalysisCacheViewer.fingerprint(
                task.userType().name(),
                task.usageIdentifierKeys()
        );
        when(cache.findHealth(cacheKey)).thenReturn(Optional.of(new CachedAnalysisResult<>(
                result(article()),
                NOW.plus(Duration.ofDays(3)),
                ArticleRevisionFingerprint.from(article())
        )));
        when(useCase.read(task.articleUrl())).thenReturn(article());
        when(usagePolicy.recordCacheAccess(task.usageSubject(), cacheKey, viewerFingerprint))
                .thenReturn(Optional.of(new HealthTopicFailureUsageResult(true, 2, 5)));
        store.outcomeWriteFailuresRemaining = 1;
        Clock clock = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                cache,
                versions,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        HealthAnalysisJobService.Usage usage = new ObjectMapper().readValue(
                outcome.usageJson(),
                HealthAnalysisJobService.Usage.class
        );
        assertThat(outcome.errorCode()).isEqualTo("ANALYSIS_FAILED");
        assertThat(usage).isEqualTo(new HealthAnalysisJobService.Usage(5, 2, 3, true));
    }

    /** 기사 수집 실패의 종료 상태와 무재시도 */
    @Test
    void failsDequeuedTaskWithoutRetry() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        var queue = new SingleTaskQueue(task, true);
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        when(useCase.screen(task.articleUrl(), task.usageSubject()))
                .thenThrow(new ArticleProcessingException(ArticleProcessingError.DOWNLOAD_FAILED));
        HealthAnalysisWorker worker = worker(
                store,
                queue,
                useCase,
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isTrue();
        assertThat(worker.runOnce()).isFalse();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        assertThat(outcome.errorCode()).isEqualTo("DOWNLOAD_FAILED");
        assertThat(queue.deliveryCount).isEqualTo(1);
    }

    /** 이용량 차감 후 분석 실패의 차감 결과 보존 */
    @Test
    void preservesChargedUsageWhenAnalysisFailsAfterCharge() throws Exception {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        HealthArticleScreeningResult screening = screeningResult();
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenReturn(screening);
        when(usagePolicy.recordAnalysisStart(task.usageSubject()))
                .thenReturn(new HealthTopicFailureUsageResult(true, 2, 5));
        when(useCase.continueAfterScreening(screening, task.usageSubject(), accepted.deadlineAt()))
                .thenThrow(new IllegalStateException("analysis failed"));

        assertThat(worker(
                store,
                new SingleTaskQueue(task, true),
                useCase,
                usagePolicy,
                NOW
        ).runOnce()).isTrue();

        AnalysisJobOutcome outcome = store.findOutcome(accepted.id()).orElseThrow();
        HealthAnalysisJobService.Usage usage = new ObjectMapper().readValue(
                outcome.usageJson(),
                HealthAnalysisJobService.Usage.class
        );
        assertThat(outcome.errorCode()).isEqualTo("ANALYSIS_FAILED");
        assertThat(usage).isEqualTo(new HealthAnalysisJobService.Usage(5, 2, 3, true));
    }

    /** Queue 대기를 포함한 90초 Deadline 이후 실행 차단 */
    @Test
    void failsExpiredQueuedTaskBeforeArticleRead() {
        var store = new InMemoryJobStore();
        Instant acceptedAt = NOW.minusSeconds(90);
        AnalysisJob accepted = lifecycle(store, acceptedAt).accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthAnalysisWorker worker = worker(
                store,
                new SingleTaskQueue(task, true),
                useCase,
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().errorCode())
                .isEqualTo("ANALYSIS_DEADLINE_EXCEEDED");
        verify(useCase, never()).screen(task.articleUrl(), task.usageSubject());
    }

    /** 기사 수집 중 Deadline 경과 시 분석 시작과 차감 차단 */
    @Test
    void stopsBeforeAnalysisWhenDeadlinePassesDuringScreening() {
        var store = new InMemoryJobStore();
        var clock = new MutableClock(NOW);
        AnalysisJobLifecycleService lifecycle = new AnalysisJobLifecycleService(store, clock);
        AnalysisJob accepted = lifecycle.accept("analysis-1", OWNER);
        HealthAnalysisTask task = task(accepted.id());
        HealthAnalysisUseCase useCase = mock(HealthAnalysisUseCase.class);
        HealthTopicFailureUsagePolicy usagePolicy = mock(HealthTopicFailureUsagePolicy.class);
        when(useCase.screen(task.articleUrl(), task.usageSubject())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(90));
            return new HealthArticleScreeningResult(
                    article(),
                    HealthArticleTopicDecision.HEALTH_RELATED
            );
        });
        HealthAnalysisWorker worker = new HealthAnalysisWorker(
                new SingleTaskQueue(task, true),
                lifecycle,
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                clock,
                "test-worker"
        );

        assertThat(worker.runOnce()).isTrue();

        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.FAILED);
        assertThat(store.findOutcome(accepted.id()).orElseThrow().errorCode())
                .isEqualTo("ANALYSIS_DEADLINE_EXCEEDED");
        verify(usagePolicy, never()).recordAnalysisStart(task.usageSubject());
        verify(useCase, never()).continueAfterScreening(
                screeningResult(),
                task.usageSubject(),
                accepted.deadlineAt()
        );
    }

    /** Worker Lease 획득 실패 시 Queue 미소비 */
    @Test
    void doesNotConsumeWhenAnotherWorkerOwnsLease() {
        var store = new InMemoryJobStore();
        AnalysisJob accepted = lifecycle(store, NOW).accept("analysis-1", OWNER);
        var queue = new SingleTaskQueue(task(accepted.id()), false);
        HealthAnalysisWorker worker = worker(
                store,
                queue,
                mock(HealthAnalysisUseCase.class),
                mock(HealthTopicFailureUsagePolicy.class),
                NOW
        );

        assertThat(worker.runOnce()).isFalse();
        assertThat(queue.takeCount).isZero();
        assertThat(store.findById(accepted.id()).orElseThrow().status())
                .isEqualTo(AnalysisJobStatus.PROCESSING);
    }

    /** 고정 의존성 Worker 구성 */
    private HealthAnalysisWorker worker(
            InMemoryJobStore store,
            HealthAnalysisQueue queue,
            HealthAnalysisUseCase useCase,
            HealthTopicFailureUsagePolicy usagePolicy,
            Instant now
    ) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new HealthAnalysisWorker(
                queue,
                new AnalysisJobLifecycleService(store, clock),
                store,
                store,
                useCase,
                usagePolicy,
                new ObjectMapper(),
                clock,
                "test-worker"
        );
    }

    /** 고정 시각 작업 수명 Service */
    private AnalysisJobLifecycleService lifecycle(InMemoryJobStore store, Instant now) {
        return new AnalysisJobLifecycleService(store, Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Queue 작업 Fixture */
    private HealthAnalysisTask task(String analysisId) {
        return new HealthAnalysisTask(
                analysisId,
                "https://news.example/article",
                HealthAnalysisUserType.MEMBER,
                List.of("member-usage-key")
        );
    }

    /** 추출 기사 Fixture */
    private ExtractedArticle article() {
        return new ExtractedArticle(
                URI.create("https://news.example/article"),
                "독감 예방접종 대상 안내",
                "질병관리청은 독감 예방접종 대상과 시기를 안내했습니다.",
                OffsetDateTime.parse("2026-09-18T09:00:00+09:00"),
                Optional.empty()
        );
    }

    /** 건강 기사 분야 판별 Fixture */
    private HealthArticleScreeningResult screeningResult() {
        return new HealthArticleScreeningResult(
                article(),
                HealthArticleTopicDecision.HEALTH_RELATED
        );
    }

    /** Mock 분석 결과 Fixture */
    private HealthAnalysisResult result(ExtractedArticle article) {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        null
                ),
                NOW,
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.ZERO.setScale(2),
                0,
                1,
                List.of(new HealthAnalysisResult.Claim(
                        1,
                        article.title(),
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "Mock 분석에서는 근거 검색을 수행하지 않습니다.",
                        List.of()
                )),
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                false
        );
    }

    /** 단일 근거 포함 분석 결과 Fixture */
    private HealthAnalysisResult resultWithEvidence(
            ExtractedArticle article,
            URI sourceUrl,
            HealthAnalysisResult.ClaimStatus status
    ) {
        return resultWithClaims(article, List.of(evidenceClaim(1, sourceUrl, status)));
    }

    /** 정상·깨진 근거 포함 분석 결과 Fixture */
    private HealthAnalysisResult resultWithTwoEvidenceClaims(
            ExtractedArticle article,
            URI availableUrl,
            URI missingUrl
    ) {
        return resultWithClaims(article, List.of(
                evidenceClaim(1, availableUrl, HealthAnalysisResult.ClaimStatus.SUPPORTED),
                evidenceClaim(2, missingUrl, HealthAnalysisResult.ClaimStatus.CONTRADICTED)
        ));
    }

    /** 주장 목록 기반 분석 결과 Fixture */
    private HealthAnalysisResult resultWithClaims(
            ExtractedArticle article,
            List<HealthAnalysisResult.Claim> claims
    ) {
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        null
                ),
                NOW,
                HealthAnalysisResult.OverallStatus.CAUTION,
                BigDecimal.valueOf(50).setScale(2),
                1,
                claims.size(),
                claims,
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                "mock-health-analysis-v1",
                "health-analysis-policy-v1",
                "evidence-allowlist-v1",
                false
        );
    }

    /** 단일 근거 주장 Fixture */
    private HealthAnalysisResult.Claim evidenceClaim(
            int order,
            URI sourceUrl,
            HealthAnalysisResult.ClaimStatus status
    ) {
        return new HealthAnalysisResult.Claim(
                order,
                "근거 포함 주장 " + order,
                status,
                "판정 이유",
                List.of(new HealthAnalysisResult.Evidence(
                        HealthAnalysisResult.EvidenceSourceKind.OFFICIAL,
                        "source-" + order,
                        HealthAnalysisResult.EvidenceStudyType.GUIDELINE,
                        HealthAnalysisResult.EvidenceRelationType.SUPPORTS,
                        "근거 자료",
                        "공식 기관",
                        java.time.LocalDate.parse("2026-09-30"),
                        sourceUrl,
                        "근거 요약",
                        null
                ))
        );
    }

    /** 테스트용 작업과 결과 저장소 */
    private static final class InMemoryJobStore implements AnalysisJobStore, AnalysisJobOutcomeStore {

        private final Map<String, AnalysisJob> jobs = new HashMap<>();
        private final Map<String, AnalysisJobOutcome> outcomes = new HashMap<>();
        private boolean rejectOutcomeWrites;
        private int outcomeWriteFailuresRemaining;

        @Override
        public boolean create(AnalysisJob job) {
            return jobs.putIfAbsent(job.id(), job) == null;
        }

        @Override
        public Optional<AnalysisJob> findById(String jobId) {
            return Optional.ofNullable(jobs.get(jobId));
        }

        @Override
        public boolean replace(String jobId, long expectedVersion, AnalysisJob updatedJob) {
            AnalysisJob current = jobs.get(jobId);
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            jobs.put(jobId, updatedJob);
            return true;
        }

        @Override
        public boolean replaceWithOutcome(
                String jobId,
                long expectedVersion,
                AnalysisJob updatedJob,
                AnalysisJobOutcome outcome
        ) {
            if (outcomeWriteFailuresRemaining > 0) {
                outcomeWriteFailuresRemaining--;
                throw new IllegalStateException("outcome write failed");
            }
            if (rejectOutcomeWrites) {
                return false;
            }
            if (!replace(jobId, expectedVersion, updatedJob)) {
                return false;
            }
            outcomes.put(jobId, outcome);
            return true;
        }

        @Override
        public Optional<AnalysisJobOutcome> findOutcome(String jobId) {
            return Optional.ofNullable(outcomes.get(jobId));
        }
    }

    /** 단일 작업과 Lease 상태 Queue */
    private static final class SingleTaskQueue implements HealthAnalysisQueue {

        private HealthAnalysisTask task;
        private final boolean leaseAvailable;
        private int takeCount;
        private int deliveryCount;

        private SingleTaskQueue(HealthAnalysisTask task, boolean leaseAvailable) {
            this.task = task;
            this.leaseAvailable = leaseAvailable;
        }

        @Override
        public boolean enqueue(HealthAnalysisTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HealthAnalysisTask> take() {
            takeCount++;
            HealthAnalysisTask current = task;
            task = null;
            if (current != null) {
                deliveryCount++;
            }
            return Optional.ofNullable(current);
        }

        @Override
        public boolean tryAcquireWorker(String ownerToken, Duration leaseTime) {
            return leaseAvailable;
        }

        @Override
        public void releaseWorker(String ownerToken) {
        }
    }

    /** 단계별 Deadline 이동용 Clock */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /** 현재 시각 이동 */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
