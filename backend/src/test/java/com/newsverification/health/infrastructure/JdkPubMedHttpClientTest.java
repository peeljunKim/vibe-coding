/* PubMed 응답 본문 제한 검증 */
package com.newsverification.health.infrastructure;

import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응답 완료 전 크기 초과 차단 */
class JdkPubMedHttpClientTest {

    /** 제한 안의 분할 응답 결합 */
    @Test
    void collectsBodyWithinLimit() {
        HttpResponse.BodySubscriber<byte[]> subscriber = JdkPubMedHttpClient.limitedBodySubscriber(5);
        subscriber.onSubscribe(new TestSubscription());

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3, 4, 5})));
        subscriber.onComplete();

        assertThat(subscriber.getBody().toCompletableFuture().join())
                .containsExactly(1, 2, 3, 4, 5);
    }

    /** 제한을 넘는 응답의 즉시 취소 */
    @Test
    void rejectsBodyOverLimit() {
        HttpResponse.BodySubscriber<byte[]> subscriber = JdkPubMedHttpClient.limitedBodySubscriber(4);
        var subscription = new TestSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5})));

        assertThat(subscription.cancelled).isTrue();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasMessageContaining("PubMed response exceeded size limit");
    }

    /** 요청·취소 상태 기록 */
    private static final class TestSubscription implements Flow.Subscription {

        private boolean cancelled;

        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
