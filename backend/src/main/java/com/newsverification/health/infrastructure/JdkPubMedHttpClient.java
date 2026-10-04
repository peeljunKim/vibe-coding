/* JDK 기반 PubMed HTTP Client */
package com.newsverification.health.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 고정 NCBI Endpoint 제한 요청 Client */
final class JdkPubMedHttpClient implements PubMedHttpClient {

    private final HttpClient client;

    /** Redirect 차단과 연결 제한 구성 */
    JdkPubMedHttpClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** 제한된 PubMed 응답 수신 */
    @Override
    public Response get(URI uri, Duration timeout, int maxResponseBytes)
            throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"eutils.ncbi.nlm.nih.gov".equalsIgnoreCase(uri.getHost())
                || timeout.isZero()
                || timeout.isNegative()
                || maxResponseBytes < 1) {
            throw new IllegalArgumentException("Invalid PubMed HTTP request");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json, application/xml")
                .header("User-Agent", "news-verification/0.1")
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        try (var input = response.body()) {
            byte[] bytes = input.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new IOException("PubMed response exceeded size limit");
            }
            return new Response(
                    response.statusCode(),
                    new String(bytes, StandardCharsets.UTF_8)
            );
        }
    }
}
