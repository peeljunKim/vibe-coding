/* 기사 URL 보안 정책 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 공개 HTTPS 기사 URL 검증 */
class ArticleUrlValidatorTest {

    private final HostResolver publicHostResolver = hostname ->
            List.of(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}));

    /** 허용 언론사의 공개 HTTPS URL 승인 */
    @Test
    void acceptsPublicHttpsUrlFromAllowedPublisher() {
        var validator = new ArticleUrlValidator(publicHostResolver);

        var result = validator.validate(
                "https://news.example/article/123?source=home",
                Set.of("news.example")
        );

        assertThat(result.uri()).isEqualTo(URI.create("https://news.example/article/123?source=home"));
        assertThat(result.addresses()).extracting(InetAddress::getHostAddress).containsExactly("1.1.1.1");
    }

    /** 내부 주소로 해석되는 URL 차단 */
    @Test
    void rejectsUrlResolvedToPrivateAddress() {
        HostResolver privateHostResolver = hostname ->
                List.of(InetAddress.getByAddress(new byte[]{10, 0, 0, 1}));
        var validator = new ArticleUrlValidator(privateHostResolver);

        assertThatThrownBy(() -> validator.validate(
                "https://news.example/article/123",
                Set.of("news.example")
        ))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(ArticleProcessingError.UNSAFE_ADDRESS);
    }

    /** HTTPS 외 Scheme 차단 */
    @Test
    void rejectsNonHttpsUrl() {
        assertError(
                "http://news.example/article/123",
                Set.of("news.example"),
                ArticleProcessingError.UNSUPPORTED_SCHEME
        );
    }

    /** URL 사용자 인증정보 차단 */
    @Test
    void rejectsUrlWithUserInformation() {
        assertError(
                "https://user:password@news.example/article/123",
                Set.of("news.example"),
                ArticleProcessingError.USER_INFO_NOT_ALLOWED
        );
    }

    /** HTTPS 기본 Port 외 연결 차단 */
    @Test
    void rejectsCustomPort() {
        assertError(
                "https://news.example:8443/article/123",
                Set.of("news.example"),
                ArticleProcessingError.PORT_NOT_ALLOWED
        );
    }

    /** 미등록 언론사 Host 차단 */
    @Test
    void rejectsUnlistedPublisherHost() {
        assertError(
                "https://unknown.example/article/123",
                Set.of("news.example"),
                ArticleProcessingError.UNSUPPORTED_PUBLISHER
        );
    }

    /** URL 실패 코드 확인 */
    private void assertError(String rawUrl, Set<String> allowedHosts, ArticleProcessingError error) {
        var validator = new ArticleUrlValidator(publicHostResolver);
        assertThatThrownBy(() -> validator.validate(rawUrl, allowedHosts))
                .isInstanceOf(ArticleProcessingException.class)
                .extracting(exception -> ((ArticleProcessingException) exception).error())
                .isEqualTo(error);
    }
}
