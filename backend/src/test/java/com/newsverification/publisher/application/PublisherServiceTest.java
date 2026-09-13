/* 지원 언론사 조회 서비스 검증 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherCategory;
import com.newsverification.publisher.domain.PublisherStatus;
import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 공개 상태 변환과 전체 목록 조회 검증 */
@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private NewsPublisherRepository publisherRepository;

    @InjectMocks
    private PublisherService publisherService;

    /** 언론사 분류와 무관한 공개 응답 변환 검증 */
    @Test
    void findsPublisherDirectoryAcrossCategoriesWithPublicStatuses() {
        var active = publisher("연합뉴스", PublisherCategory.NEWS_AGENCY, PublisherStatus.ACTIVE);
        var activeGeneral = publisher("한겨레", PublisherCategory.GENERAL_NEWSPAPER, PublisherStatus.ACTIVE);
        var pausedAuto = publisher("헬스조선", PublisherCategory.HEALTH_MEDICAL, PublisherStatus.PAUSED_AUTO);
        var pausedManual = publisher("한국경제", PublisherCategory.BUSINESS_NEWSPAPER, PublisherStatus.PAUSED_MANUAL);
        var candidate = publisher("뉴시스", PublisherCategory.NEWS_AGENCY, PublisherStatus.CANDIDATE);
        when(publisherRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(active, activeGeneral, pausedAuto, pausedManual, candidate));

        var result = publisherService.findPublisherDirectory();

        assertThat(result).containsExactly(
                new PublisherDirectoryEntry("연합뉴스", PublisherCategory.NEWS_AGENCY, PublisherAvailability.ACTIVE),
                new PublisherDirectoryEntry("한겨레", PublisherCategory.GENERAL_NEWSPAPER, PublisherAvailability.ACTIVE),
                new PublisherDirectoryEntry("헬스조선", PublisherCategory.HEALTH_MEDICAL, PublisherAvailability.TEMPORARILY_DISABLED),
                new PublisherDirectoryEntry("한국경제", PublisherCategory.BUSINESS_NEWSPAPER, PublisherAvailability.TEMPORARILY_DISABLED),
                new PublisherDirectoryEntry("뉴시스", PublisherCategory.NEWS_AGENCY, PublisherAvailability.UNSUPPORTED)
        );
        verify(publisherRepository).findAllByOrderByNameAsc();
    }

    /** 테스트용 언론사 모델 구성 */
    private NewsPublisher publisher(String name, PublisherCategory category, PublisherStatus status) {
        var publisher = org.mockito.Mockito.mock(NewsPublisher.class);
        when(publisher.name()).thenReturn(name);
        when(publisher.category()).thenReturn(category);
        when(publisher.status()).thenReturn(status);
        return publisher;
    }
}
