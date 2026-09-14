/* DB 언론사 상태와 안전 기사 수집 연결 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.publisher.application.PublisherDomainAccessService;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** DB 허용 판정 이전의 외부 요청 차단 검증 */
class PublisherArticleReaderTest {

    private static final String ARTICLE_HTML = """
            <html lang="ko"><head>
            <meta property="og:title" content="건강 기사 제목">
            <meta property="article:published_time" content="2026-08-14T09:30:00+09:00">
            </head><body><article><p>검증할 건강 기사 본문입니다.</p></article></body></html>
            """;

    /** 활성 호스트의 기존 안전 수집 흐름 연결 */
    @Test
    void readsArticleAfterPublisherAccessApproval() throws Exception {
        PublisherDomainAccessService accessService = mock(PublisherDomainAccessService.class);
        when(accessService.requireActivePublisherHosts("news.example"))
                .thenReturn(Set.of("news.example"));
        AtomicInteger requestCount = new AtomicInteger();
        PublisherArticleReader reader = reader(accessService, requestCount);

        var article = reader.read("https://NEWS.example/article/1");

        assertThat(article.title()).isEqualTo("건강 기사 제목");
        assertThat(requestCount).hasValue(1);
        verify(accessService).requireActivePublisherHosts("news.example");
    }

    /** 후보·중단·미등록 상태의 외부 요청 전 차단 */
    @Test
    void doesNotCallExternalHttpWhenPublisherAccessIsRejected() throws Exception {
        PublisherDomainAccessService accessService = mock(PublisherDomainAccessService.class);
        when(accessService.requireActivePublisherHosts("news.example"))
                .thenThrow(new ArticleProcessingException(
                        ArticleProcessingError.PUBLISHER_TEMPORARILY_DISABLED));
        AtomicInteger requestCount = new AtomicInteger();
        PublisherArticleReader reader = reader(accessService, requestCount);

        assertThatThrownBy(() -> reader.read("https://news.example/article/1"))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.PUBLISHER_TEMPORARILY_DISABLED);
        assertThat(requestCount).hasValue(0);
    }

    /** 실제 보안·추출 구성의 테스트 Reader */
    private PublisherArticleReader reader(
            PublisherDomainAccessService accessService,
            AtomicInteger requestCount
    ) throws Exception {
        HostResolver resolver = hostname -> List.of(
                InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));
        ArticleUrlValidator validator = new ArticleUrlValidator(resolver);
        ArticleHttpClient httpClient = (target, timeout, maxResponseBytes) -> {
            requestCount.incrementAndGet();
            return new ArticleHttpResponse(
                    200,
                    "text/html; charset=UTF-8",
                    ARTICLE_HTML,
                    ARTICLE_HTML.getBytes(StandardCharsets.UTF_8).length,
                    null
            );
        };
        SafeArticleReader safeReader = new SafeArticleReader(
                validator,
                new ArticleHtmlExtractor(),
                httpClient,
                Duration.ofSeconds(10),
                2 * 1024 * 1024,
                3
        );
        return new PublisherArticleReader(validator, accessService, safeReader);
    }
}
