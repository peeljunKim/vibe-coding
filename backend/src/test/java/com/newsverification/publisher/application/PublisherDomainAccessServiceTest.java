/* 언론사 도메인 상태별 기사 추출 허용 검증 */
package com.newsverification.publisher.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.publisher.domain.NewsPublisher;
import com.newsverification.publisher.domain.NewsPublisherDomain;
import com.newsverification.publisher.domain.PublisherAvailability;
import com.newsverification.publisher.domain.PublisherDomainStatus;
import com.newsverification.publisher.infrastructure.NewsPublisherDomainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 활성·중단·후보·미등록 Host 판정 */
@ExtendWith(MockitoExtension.class)
class PublisherDomainAccessServiceTest {

    @Mock
    private NewsPublisherDomainRepository domainRepository;

    @InjectMocks
    private PublisherDomainAccessService service;

    /** 활성 언론사의 활성 별칭 전체 허용 */
    @Test
    void returnsActiveHostsFromTheSamePublisher() {
        NewsPublisher publisher = mock(NewsPublisher.class);
        NewsPublisherDomain requested = mock(NewsPublisherDomain.class);
        NewsPublisherDomain alias = mock(NewsPublisherDomain.class);
        when(requested.publisher()).thenReturn(publisher);
        when(requested.hostname()).thenReturn("news.example");
        when(requested.availability()).thenReturn(PublisherAvailability.ACTIVE);
        when(alias.hostname()).thenReturn("m.news.example");
        when(domainRepository.findByHostname("news.example")).thenReturn(Optional.of(requested));
        when(domainRepository.findAllByPublisherAndStatusOrderByHostnameAsc(
                publisher, PublisherDomainStatus.ACTIVE)).thenReturn(List.of(alias, requested));

        var allowedHosts = service.requireActivePublisherHosts("news.example");

        assertThat(allowedHosts).containsExactlyInAnyOrder("news.example", "m.news.example");
    }

    /** 후보 언론사 추출 차단 */
    @Test
    void rejectsCandidatePublisher() {
        assertBlocked(PublisherAvailability.UNSUPPORTED, ArticleProcessingError.UNSUPPORTED_PUBLISHER);
    }

    /** 언론사 또는 도메인 일시 중단 차단 */
    @Test
    void rejectsTemporarilyDisabledPublisherDomain() {
        assertBlocked(
                PublisherAvailability.TEMPORARILY_DISABLED,
                ArticleProcessingError.PUBLISHER_TEMPORARILY_DISABLED
        );
    }

    /** 미등록 호스트 추출 차단 */
    @Test
    void rejectsUnregisteredHost() {
        when(domainRepository.findByHostname("unknown.example")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActivePublisherHosts("unknown.example"))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.UNSUPPORTED_PUBLISHER);
    }

    /** 차단 상태와 오류 코드 검증 */
    private void assertBlocked(PublisherAvailability availability, ArticleProcessingError expectedError) {
        NewsPublisherDomain requested = mock(NewsPublisherDomain.class);
        when(requested.availability()).thenReturn(availability);
        when(domainRepository.findByHostname("news.example")).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> service.requireActivePublisherHosts("news.example"))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(expectedError);
        verify(domainRepository, never()).findAllByPublisherAndStatusOrderByHostnameAsc(
                any(), eq(PublisherDomainStatus.ACTIVE));
    }
}
