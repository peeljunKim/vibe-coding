/* 검증 URL과 고정 DNS 주소 전달 */
package com.newsverification.article.application;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/** URL Validator에서 HTTP 연결까지 유지하는 요청 목적지 */
public record ResolvedArticleUrl(URI uri, String hostname, List<InetAddress> addresses) {
    public ResolvedArticleUrl {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(hostname);
        addresses = List.copyOf(addresses);
        if (addresses.isEmpty() || !"https".equalsIgnoreCase(uri.getScheme())
                || !hostname.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Invalid resolved article destination");
        }
    }
}
