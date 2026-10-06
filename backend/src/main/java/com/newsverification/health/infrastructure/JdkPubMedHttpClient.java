/* JDK 기반 PubMed HTTP Client */
package com.newsverification.health.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

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
        HttpResponse<byte[]> response = client.send(
                request,
                responseInfo -> limitedBodySubscriber(maxResponseBytes)
        );
        return new Response(
                response.statusCode(),
                new String(response.body(), StandardCharsets.UTF_8)
        );
    }

    /** 본문 수신 중 크기 제한 적용 */
    static HttpResponse.BodySubscriber<byte[]> limitedBodySubscriber(int maxResponseBytes) {
        return new LimitedBodySubscriber(maxResponseBytes);
    }

    /** 제한 크기까지만 누적하는 응답 Subscriber */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxResponseBytes;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBodySubscriber(int maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (output.size() + length > maxResponseBytes) {
                    subscription.cancel();
                    body.completeExceptionally(
                            new IOException("PubMed response exceeded size limit")
                    );
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                output.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
