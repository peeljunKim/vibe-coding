/* 언론사 자동 중단 상태 전환 검증 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.infrastructure.NewsPublisherRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ACTIVE 조건부 갱신과 표시명 반환 검증 */
class PublisherStatusUpdaterTest {

    /** 활성 언론사의 단일 자동 중단 */
    @Test
    void pausesActivePublisherAndReturnsDisplayName() {
        NewsPublisherRepository publisherRepository = mock(NewsPublisherRepository.class);
        NewsPublisher publisher = mock(NewsPublisher.class);
        when(publisherRepository.findById(7L)).thenReturn(Optional.of(publisher));
        when(publisher.name()).thenReturn("테스트 언론사");
        when(publisherRepository.pauseAutomatically(7L)).thenReturn(1);
        PublisherStatusUpdater updater = new PublisherStatusUpdater(publisherRepository);

        Optional<String> result = updater.pauseAutomatically(7L);

        assertThat(result).contains("테스트 언론사");
        verify(publisherRepository).pauseAutomatically(7L);
    }

    /** 이미 중단된 언론사의 중복 전환 차단 */
    @Test
    void returnsEmptyWhenConditionalUpdateDoesNotMatch() {
        NewsPublisherRepository publisherRepository = mock(NewsPublisherRepository.class);
        NewsPublisher publisher = mock(NewsPublisher.class);
        when(publisherRepository.findById(7L)).thenReturn(Optional.of(publisher));
        when(publisherRepository.pauseAutomatically(7L)).thenReturn(0);
        PublisherStatusUpdater updater = new PublisherStatusUpdater(publisherRepository);

        Optional<String> result = updater.pauseAutomatically(7L);

        assertThat(result).isEmpty();
    }
}
