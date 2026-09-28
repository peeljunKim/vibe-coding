/* 공유 링크 Application 동작 검증 */
package com.newsverification.share.application;

import com.newsverification.analysis.domain.AnalysisJobStage;
import com.newsverification.analysis.domain.AnalysisJobStatus;
import com.newsverification.headline.application.HeadlineAnalysisJobService;
import com.newsverification.headline.application.HeadlineAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 소유권·만료·공개 조회 경계 검증 */
class DefaultShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T01:00:00Z");
    private ShareStore store;
    private HeadlineAnalysisJobService headlineJobs;
    private DefaultShareService service;

    @BeforeEach
    void setUp() {
        store = mock(ShareStore.class);
        headlineJobs = mock(HeadlineAnalysisJobService.class);
        service = new DefaultShareService(
                store,
                headlineJobs,
                () -> "fixed-share-token",
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(store.isActiveUser(42L)).thenReturn(true);
    }

    @Test
    void createsSevenDayHealthShareForOwnedSavedRecord() {
        when(store.findHealthSource(31L, 42L, NOW)).thenReturn(Optional.of(healthSource()));

        ShareService.Created created = service.createHealth("42", 31L);

        assertThat(created).isEqualTo(new ShareService.Created(
                "HEALTH", "fixed-share-token", NOW.plusSeconds(7L * 24 * 60 * 60)
        ));
    }

    @Test
    void rejectsInactiveAccountBeforeReadingAnalysis() {
        when(store.isActiveUser(42L)).thenReturn(false);

        assertThatThrownBy(() -> service.createHealth("42", 31L))
                .isInstanceOf(ShareException.class)
                .hasMessage("SHARE_ACCESS_DENIED");
        verifyNoInteractions(headlineJobs);
    }

    @Test
    void createsHeadlineSnapshotFromOwnedCompletedJob() {
        HeadlineAnalysisJobService.Requester requester =
                new HeadlineAnalysisJobService.Requester("42", null, null, null);
        when(headlineJobs.find("headline-1", requester)).thenReturn(Optional.of(new HeadlineAnalysisJobService.Progress(
                "headline-1", AnalysisJobStatus.COMPLETED, AnalysisJobStage.COMPLETED,
                NOW.minusSeconds(20), NOW.plusSeconds(1_800), headlineResult(), null, null
        )));

        ShareService.Created created = service.createHeadline("42", "headline-1");

        assertThat(created.shareType()).isEqualTo("HEADLINE");
        assertThat(created.shareToken()).isEqualTo("fixed-share-token");
    }

    @Test
    void rejectsLegacyHeadlineResultWithoutVersions() {
        HeadlineAnalysisResult legacy = new HeadlineAnalysisResult(
                headlineResult().article(), NOW, headlineResult().issues(), null, null, null
        );
        when(headlineJobs.find(
                "headline-1", new HeadlineAnalysisJobService.Requester("42", null, null, null)
        )).thenReturn(Optional.of(new HeadlineAnalysisJobService.Progress(
                "headline-1", AnalysisJobStatus.COMPLETED, AnalysisJobStage.COMPLETED,
                NOW.minusSeconds(20), NOW.plusSeconds(1_800), legacy, null, null
        )));

        assertThatThrownBy(() -> service.createHeadline("42", "headline-1"))
                .isInstanceOf(ShareException.class)
                .hasMessage("SHARE_SOURCE_UNAVAILABLE");
    }

    @Test
    void readsPublicHealthSnapshotWithoutRunningHeadlineAnalysis() {
        ShareService.HealthResult shared = new ShareService.HealthResult(
                "HEALTH", NOW.plusSeconds(604_800), healthSource().article(), NOW,
                "CAUTION", "50.00", 1, 2, List.of(), "NOT_REVIEWED", true
        );
        when(store.findHealth(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq(NOW))).thenReturn(Optional.of(shared));

        assertThat(service.findHealth("valid-public-share-token")).isEqualTo(shared);

        verifyNoInteractions(headlineJobs);
    }

    private ShareStore.HealthSource healthSource() {
        return new ShareStore.HealthSource(
                31L,
                new ShareService.Article("https://news.example/article", "건강 기사", "언론사", null, null),
                NOW.minusSeconds(60), "CAUTION", "50.00", 1, 2, List.of(), "NOT_REVIEWED", true
        );
    }

    private HeadlineAnalysisResult headlineResult() {
        return new HeadlineAnalysisResult(
                new HeadlineAnalysisResult.ArticleSummary(
                        URI.create("https://news.example/article"), "기존 제목", "언론사",
                        OffsetDateTime.parse("2026-09-28T09:00:00+09:00"), null
                ),
                NOW,
                List.of(new HeadlineAnalysisResult.Issue(
                        HeadlineAnalysisResult.IssueType.NO_ISSUE, "문제 없음"
                )),
                null,
                "mock-headline-analysis-v1",
                "headline-analysis-policy-v1"
        );
    }
}
