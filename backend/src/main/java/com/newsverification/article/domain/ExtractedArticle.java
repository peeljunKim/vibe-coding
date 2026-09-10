/* 검증된 기사 추출 결과 */
package com.newsverification.article.domain;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;

/** AI 처리 전 정제된 기사 정보 */
public record ExtractedArticle(
        URI sourceUrl,
        String title,
        String body,
        OffsetDateTime publishedAt,
        Optional<OffsetDateTime> modifiedAt
) {
}
