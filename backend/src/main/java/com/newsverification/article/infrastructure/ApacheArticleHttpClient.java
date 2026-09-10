/* 검증 IP 고정 기반 기사 HTTPS 요청 */
package com.newsverification.article.infrastructure;

import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.ResolvedArticleUrl;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 원래 Host·TLS 검증을 유지하는 단일 요청 Client */
public final class ApacheArticleHttpClient implements ArticleHttpClient {
    private final SSLContext sslContext;

    public ApacheArticleHttpClient() {
        this(SSLContexts.createDefault());
    }

    ApacheArticleHttpClient(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /** 재조회·자동 Redirect·자동 Retry 없는 제한 요청 */
    @Override
    public ArticleHttpResponse get(ResolvedArticleUrl target, Duration timeout, int maxResponseBytes)
            throws IOException {
        long timeoutMillis = timeout.toMillis();
        if (timeoutMillis <= 0 || maxResponseBytes <= 0 || maxResponseBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Article request limits must be positive and bounded");
        }
        Timeout limit = Timeout.ofMilliseconds(timeoutMillis);
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(pinnedResolver(target))
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(limit).setSocketTimeout(limit).build())
                .build();
        HttpGet request = new HttpGet(target.uri());
        request.setHeader("User-Agent", "NewsVerificationBot/0.1");
        request.setConfig(RequestConfig.custom().setConnectionRequestTimeout(limit)
                .setResponseTimeout(limit).build());
        var deadline = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "article-request-deadline");
            thread.setDaemon(true);
            return thread;
        });
        try (var client = HttpClients.custom().setConnectionManager(manager)
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .disableAuthCaching().build()) {
            deadline.schedule(request::cancel, timeoutMillis, TimeUnit.MILLISECONDS);
            return client.execute(request, response -> {
                var entity = response.getEntity();
                byte[] bytes = entity == null ? new byte[0]
                        : entity.getContent().readNBytes(maxResponseBytes + 1);
                String contentType = entity == null ? null : entity.getContentType();
                var location = response.getFirstHeader("Location");
                // 초과 본문을 재사용 목적으로 끝까지 읽는 동작 차단
                if (bytes.length > maxResponseBytes) {
                    request.cancel();
                    return new ArticleHttpResponse(response.getCode(), contentType, "", bytes.length,
                            location == null ? null : location.getValue());
                }
                String body = "";
                ContentType type = ContentType.parseLenient(contentType);
                if (type != null && "text/html".equalsIgnoreCase(type.getMimeType())) {
                    String charset = type.getCharset() == null ? null : type.getCharset().name();
                    body = Jsoup.parse(new ByteArrayInputStream(bytes), charset, target.uri().toString())
                            .outerHtml();
                }
                return new ArticleHttpResponse(response.getCode(), contentType, body, bytes.length,
                        location == null ? null : location.getValue());
            });
        }
        finally {
            deadline.shutdownNow();
        }
    }

    /** 검증 당시 주소만 반환하는 요청 전용 Resolver */
    private DnsResolver pinnedResolver(ResolvedArticleUrl target) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                checkHost(host);
                return target.addresses().toArray(InetAddress[]::new);
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                checkHost(host);
                return target.hostname();
            }

            private void checkHost(String host) throws UnknownHostException {
                if (!target.hostname().equalsIgnoreCase(host)) {
                    throw new UnknownHostException("Unvalidated HTTP destination");
                }
            }
        };
    }
}
