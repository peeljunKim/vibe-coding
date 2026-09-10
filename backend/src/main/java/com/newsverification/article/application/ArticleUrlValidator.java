/* 기사 URL과 DNS 보안 검증 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/** 외부 기사 요청 전 URL 보안 정책 */
public final class ArticleUrlValidator {

    private final HostResolver hostResolver;

    public ArticleUrlValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    /** 허용 언론사와 공개 IP URL 검증 */
    public ResolvedArticleUrl validate(String rawUrl, Set<String> allowedHosts) {
        URI uri = parse(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ArticleProcessingException(ArticleProcessingError.UNSUPPORTED_SCHEME);
        }
        if (uri.getRawUserInfo() != null) {
            throw new ArticleProcessingException(ArticleProcessingError.USER_INFO_NOT_ALLOWED);
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new ArticleProcessingException(ArticleProcessingError.PORT_NOT_ALLOWED);
        }

        String hostname = normalizeHostname(uri.getHost());
        boolean allowed = allowedHosts.stream()
                .map(ArticleUrlValidator::normalizeHostname)
                .anyMatch(hostname::equals);
        if (!allowed) {
            throw new ArticleProcessingException(ArticleProcessingError.UNSUPPORTED_PUBLISHER);
        }

        try {
            var addresses = hostResolver.resolve(hostname);
            if (addresses.isEmpty() || addresses.stream().anyMatch(ArticleUrlValidator::isUnsafeAddress)) {
                throw new ArticleProcessingException(ArticleProcessingError.UNSAFE_ADDRESS);
            }
            URI normalized = URI.create("https://" + hostname
                    + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            return new ResolvedArticleUrl(normalized, hostname, addresses);
        }
        catch (UnknownHostException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.DNS_LOOKUP_FAILED, exception);
        }
    }

    /** URL 문법 확인 */
    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_URL);
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new ArticleProcessingException(ArticleProcessingError.INVALID_URL);
            }
            return uri;
        }
        catch (URISyntaxException | IllegalArgumentException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_URL, exception);
        }
    }

    /** 비교용 호스트 정규화 */
    private static String normalizeHostname(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_URL);
        }
        String withoutTrailingDot = hostname.endsWith(".")
                ? hostname.substring(0, hostname.length() - 1)
                : hostname;
        try {
            return IDN.toASCII(withoutTrailingDot, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_URL, exception);
        }
    }

    /** 내부·예약 IP 판별 */
    private static boolean isUnsafeAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 0
                    || first >= 240
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113);
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return (first & 0xfe) == 0xfc
                || (first == 0x20 && second == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
    }
}
