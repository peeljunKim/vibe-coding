/* 언론사 자동 중단 상태 전환 검증 */
package com.newsverification.publisher.application;

import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.NewsPublisherDomain;
import com.newsverification.publisher.domain.PublisherStatus;
import com.newsverification.publisher.infrastructure.NewsPublisherDomainRepository;
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
        NewsPublisherDomainRepository domainRepository = mock(NewsPublisherDomainRepository.class);
        NewsPublisher publisher = mock(NewsPublisher.class);
        NewsPublisherDomain domain = mock(NewsPublisherDomain.class);
        when(domainRepository.findByHostname("news.example")).thenReturn(Optional.of(domain));
        when(domain.publisher()).thenReturn(publisher);
        when(publisher.id()).thenReturn(7L);
        when(publisher.name()).thenReturn("테스트 언론사");
        when(publisherRepository.pauseAutomatically(
                7L, PublisherStatus.ACTIVE, PublisherStatus.PAUSED_AUTO
        )).thenReturn(1);
        PublisherStatusUpdater updater = new PublisherStatusUpdater(
                domainRepository, publisherRepository
        );

        Optional<String> result = updater.pauseAutomatically("news.example");

        assertThat(result).contains("테스트 언론사");
        verify(publisherRepository).pauseAutomatically(
                7L, PublisherStatus.ACTIVE, PublisherStatus.PAUSED_AUTO
        );
    }

    /** 이미 중단된 언론사의 중복 전환 차단 */
    @Test
    void returnsEmptyWhenConditionalUpdateDoesNotMatch() {
        NewsPublisherRepository publisherRepository = mock(NewsPublisherRepository.class);
        NewsPublisherDomainRepository domainRepository = mock(NewsPublisherDomainRepository.class);
        NewsPublisher publisher = mock(NewsPublisher.class);
        NewsPublisherDomain domain = mock(NewsPublisherDomain.class);
        when(domainRepository.findByHostname("news.example")).thenReturn(Optional.of(domain));
        when(domain.publisher()).thenReturn(publisher);
        when(publisher.id()).thenReturn(7L);
        when(publisherRepository.pauseAutomatically(
                7L, PublisherStatus.ACTIVE, PublisherStatus.PAUSED_AUTO
        )).thenReturn(0);
        PublisherStatusUpdater updater = new PublisherStatusUpdater(
                domainRepository, publisherRepository
        );

        Optional<String> result = updater.pauseAutomatically("news.example");

        assertThat(result).isEmpty();
    }
}
