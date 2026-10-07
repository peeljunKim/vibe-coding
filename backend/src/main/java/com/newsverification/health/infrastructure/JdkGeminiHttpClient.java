/* JDK 기반 Gemini HTTP Client */
package com.newsverification.health.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

/** Redirect 없는 Google Gemini 제한 요청 Client */
final class JdkGeminiHttpClient implements GeminiHttpClient {

    private static final String GEMINI_HOST = "generativelanguage.googleapis.com";

    private volatile HttpClient client;

    /** 연결 제한과 Redirect 차단 구성 */
    JdkGeminiHttpClient() {
    }

    /** API Key Header 기반 제한 요청 */
    @Override
    public Response post(Request request) throws IOException, InterruptedException {
        if (!"https".equalsIgnoreCase(request.uri().getScheme())
                || !GEMINI_HOST.equalsIgnoreCase(request.uri().getHost())
                || !request.uri().getPath().startsWith("/v1beta/models/")
                || !request.uri().getPath().endsWith(":generateContent")) {
            throw new IllegalArgumentException("Invalid Gemini endpoint");
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-goog-api-key", request.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        request.body(),
                        StandardCharsets.UTF_8
                ))
                .build();
        HttpResponse<byte[]> response = client().send(
                httpRequest,
                responseInfo -> limitedBodySubscriber(request.maxResponseBytes())
        );
        return new Response(
                response.statusCode(),
                new String(response.body(), StandardCharsets.UTF_8)
        );
    }

    /** 첫 실제 요청 시점의 HTTP Client 생성 */
    private HttpClient client() {
        HttpClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
            }
            return client;
        }
    }

    /** 응답 수신 중 크기 제한 적용 */
    static HttpResponse.BodySubscriber<byte[]> limitedBodySubscriber(int maxResponseBytes) {
        return new LimitedBodySubscriber(maxResponseBytes);
    }

    /** 제한 크기 응답 누적 Subscriber */
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
                            new IOException("Gemini response exceeded size limit")
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
