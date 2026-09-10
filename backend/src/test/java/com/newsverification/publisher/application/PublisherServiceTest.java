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

/** 공개 상태 변환과 후보 제외 조회 검증 */
@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private NewsPublisherRepository publisherRepository;

    @InjectMocks
    private PublisherService publisherService;

    /** 활성·중단 상태의 공개 응답 변환 검증 */
    @Test
    void findsSupportedPublishersWithPublicStatuses() {
        var active = publisher("연합뉴스", PublisherCategory.NEWS_AGENCY, PublisherStatus.ACTIVE);
        var pausedAuto = publisher("헬스조선", PublisherCategory.HEALTH_MEDICAL, PublisherStatus.PAUSED_AUTO);
        var pausedManual = publisher("한국일보", PublisherCategory.GENERAL_NEWSPAPER, PublisherStatus.PAUSED_MANUAL);
        when(publisherRepository.findByStatusNotOrderByNameAsc(PublisherStatus.CANDIDATE))
                .thenReturn(List.of(active, pausedAuto, pausedManual));

        var result = publisherService.findSupportedPublishers();

        assertThat(result).containsExactly(
                new SupportedPublisher("연합뉴스", PublisherCategory.NEWS_AGENCY, PublisherAvailability.ACTIVE),
                new SupportedPublisher("헬스조선", PublisherCategory.HEALTH_MEDICAL, PublisherAvailability.TEMPORARILY_DISABLED),
                new SupportedPublisher("한국일보", PublisherCategory.GENERAL_NEWSPAPER, PublisherAvailability.TEMPORARILY_DISABLED)
        );
        verify(publisherRepository).findByStatusNotOrderByNameAsc(PublisherStatus.CANDIDATE);
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
