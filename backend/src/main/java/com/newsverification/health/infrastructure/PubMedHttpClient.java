/* PubMed 고정 Endpoint HTTP 요청 경계 */
package com.newsverification.health.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

/** NCBI 응답 크기와 시간 제한 경계 */
@FunctionalInterface
interface PubMedHttpClient {

    /** Redirect 없는 단일 PubMed GET 요청 */
    Response get(URI uri, Duration timeout, int maxResponseBytes)
            throws IOException, InterruptedException;

    /** PubMed HTTP 상태와 제한된 본문 */
    record Response(int statusCode, String body) {
    }
}
