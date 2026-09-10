/* 로컬 HTTPS 기반 IP 고정과 TLS 검증 */
package com.newsverification.article.infrastructure;

import com.newsverification.article.application.ResolvedArticleUrl;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 외부 DNS·언론사에 의존하지 않는 실제 HTTP 전송 검증 */
class ApacheArticleHttpClientTest {
    @TempDir
    static Path directory;
    static SSLContext tls;

    /** 테스트 실행 중에만 존재하는 인증서와 신뢰 저장소 */
    @BeforeAll
    static void prepareTls() throws Exception {
        Path store = directory.resolve("fixture.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "fixture",
                "-keyalg", "RSA", "-storetype", "PKCS12", "-keystore", store.toString(),
                "-storepass", "test-only", "-keypass", "test-only", "-dname", "CN=news.invalid",
                "-ext", "SAN=dns:news.invalid", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).isTrue();
        assertThat(process.exitValue()).isZero();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, "test-only".toCharArray());
        }
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, "test-only".toCharArray());
        TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(keys);
        tls = SSLContext.getInstance("TLS");
        tls.init(km.getKeyManagers(), tm.getTrustManagers(), null);
    }

    /** DNS에 없는 호스트의 고정 IP 연결과 원래 Host 유지 */
    @Test
    void connectsToPinnedAddressWithoutResolvingHostname() throws Exception {
        AtomicReference<String> host = new AtomicReference<>();
        AtomicReference<String> serverName = new AtomicReference<>();
        HttpsServer server = server();
        server.createContext("/article", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            var session = (javax.net.ssl.ExtendedSSLSession)
                    ((com.sun.net.httpserver.HttpsExchange) exchange).getSSLSession();
            serverName.set(((javax.net.ssl.SNIHostName) session.getRequestedServerNames().get(0)).getAsciiName());
            byte[] body = "<article>한국어 기사</article>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var response = new ApacheArticleHttpClient(tls).get(target(server, "news.invalid"),
                    Duration.ofSeconds(3), 1024);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("한국어 기사");
            assertThat(host.get()).isEqualTo("news.invalid:" + server.getAddress().getPort());
            assertThat(serverName.get()).isEqualTo("news.invalid");
        }
        finally { server.stop(0); }
    }

    /** 고정 IP라도 인증서 호스트 불일치 차단 */
    @Test
    void rejectsCertificateForDifferentHostname() throws Exception {
        HttpsServer server = server();
        server.start();
        try {
            assertThatThrownBy(() -> new ApacheArticleHttpClient(tls).get(target(server, "other.invalid"),
                    Duration.ofSeconds(3), 1024)).isInstanceOf(javax.net.ssl.SSLException.class);
        }
        finally { server.stop(0); }
    }

    /** 자동 Redirect 없이 응답을 Reader에 반환 */
    @Test
    void leavesRedirectValidationToReader() throws Exception {
        HttpsServer server = server();
        server.createContext("/article", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://internal.invalid/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            var response = new ApacheArticleHttpClient(tls).get(target(server, "news.invalid"),
                    Duration.ofSeconds(3), 1024);
            assertThat(response.statusCode()).isEqualTo(302);
            assertThat(response.location()).isEqualTo("https://internal.invalid/private");
        }
        finally { server.stop(0); }
    }

    /** 압축 해제 후에도 응답 크기 제한 */
    @Test
    void boundsDecompressedBodyAndPreservesOverflowSignal() throws Exception {
        var buffer = new java.io.ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) {
            gzip.write("가".repeat(2000).getBytes(StandardCharsets.UTF_8));
        }
        byte[] compressed = buffer.toByteArray();
        HttpsServer server = server();
        server.createContext("/article", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, compressed.length);
            try (var output = exchange.getResponseBody()) { output.write(compressed); }
        });
        server.start();
        try {
            var response = new ApacheArticleHttpClient(tls).get(target(server, "news.invalid"),
                    Duration.ofSeconds(3), 100);
            assertThat(response.bodyBytes()).isEqualTo(101);
            assertThat(response.body()).isEmpty();
        }
        finally { server.stop(0); }
    }

    /** 응답 지연의 유한 시간 실패 */
    @Test
    void stopsRequestWhenResponseDoesNotArrive() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        HttpsServer server = server();
        server.createContext("/article", exchange -> {
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                    assertThatThrownBy(() -> new ApacheArticleHttpClient(tls).get(
                            target(server, "news.invalid"), Duration.ofMillis(300), 1024))
                            .isInstanceOf(java.io.IOException.class));
        }
        finally { release.countDown(); server.stop(0); }
    }

    /** 운영 Client의 기본 신뢰 저장소 유지 */
    @Test
    void rejectsUntrustedTestCertificateWithProductionClient() throws Exception {
        HttpsServer server = server();
        server.start();
        try {
            assertThatThrownBy(() -> new ApacheArticleHttpClient().get(target(server, "news.invalid"),
                    Duration.ofSeconds(3), 1024)).isInstanceOf(javax.net.ssl.SSLException.class);
        }
        finally { server.stop(0); }
    }

    /** 계속 도착하는 작은 응답에도 전체 요청 기한 적용 */
    @Test
    void stopsTricklingResponseAtOverallDeadline() throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        HttpsServer server = server();
        server.createContext("/article", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    output.write('x');
                    output.flush();
                    if (release.await(100, TimeUnit.MILLISECONDS)) { break; }
                }
            }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException exception) { /* 요청 취소에 따른 연결 종료 */ }
        });
        server.start();
        try {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                    assertThatThrownBy(() -> new ApacheArticleHttpClient(tls).get(
                            target(server, "news.invalid"), Duration.ofSeconds(1), 1024))
                            .isInstanceOf(java.io.IOException.class));
        }
        finally { release.countDown(); server.stop(0); }
    }

    /** 로컬 서버와 임시 Port 생성 */
    private HttpsServer server() throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        return server;
    }

    /** 전송 경계 검증 전용 로컬 목적지 */
    private ResolvedArticleUrl target(HttpsServer server, String hostname) throws Exception {
        return new ResolvedArticleUrl(URI.create("https://" + hostname + ":"
                + server.getAddress().getPort() + "/article"), hostname,
                List.of(InetAddress.getByAddress(new byte[]{127, 0, 0, 1})));
    }
}
