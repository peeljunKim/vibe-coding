/* Gemini 고정 Endpoint HTTP 요청 경계 */
package com.newsverification.health.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Gemini 요청의 Secret·시간·응답 크기 제한 경계 */
@FunctionalInterface
interface GeminiHttpClient {

    /** 단일 Gemini JSON 요청 */
    Response post(Request request) throws IOException, InterruptedException;

    /** Gemini 요청에 필요한 최소 전송값 */
    record Request(
            URI uri,
            String apiKey,
            String body,
            Duration timeout,
            int maxResponseBytes
    ) {
        public Request {
            Objects.requireNonNull(uri);
            Objects.requireNonNull(timeout);
            if (apiKey == null || apiKey.isBlank()
                    || body == null || body.isBlank()
                    || timeout.isZero() || timeout.isNegative()
                    || maxResponseBytes < 1) {
                throw new IllegalArgumentException("Invalid Gemini HTTP request");
            }
        }
    }

    /** Gemini HTTP 상태와 제한된 본문 */
    record Response(int statusCode, String body) {
    }
}
