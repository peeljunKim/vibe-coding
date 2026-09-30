/* 기사 변경 감지 Fingerprint */
package com.newsverification.analysiscache.application;

import com.newsverification.article.domain.ExtractedArticle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 제목·시각·순서형 문단 Hash 묶음 */
public record ArticleRevisionFingerprint(
        String titleHash,
        String publishedAt,
        String modifiedAt,
        List<String> paragraphHashes
) {

    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    /** 필수 Fingerprint 값 검증 */
    public ArticleRevisionFingerprint {
        Objects.requireNonNull(titleHash);
        Objects.requireNonNull(publishedAt);
        paragraphHashes = List.copyOf(Objects.requireNonNull(paragraphHashes));
        if (titleHash.isBlank() || publishedAt.isBlank() || paragraphHashes.isEmpty()) {
            throw new IllegalArgumentException("Article revision fingerprint values are required");
        }
    }

    /** 정제 기사에서 비원문 Fingerprint 생성 */
    public static ArticleRevisionFingerprint from(ExtractedArticle article) {
        Objects.requireNonNull(article);
        List<String> paragraphHashes = article.body().lines()
                .map(ArticleRevisionFingerprint::normalize)
                .filter(paragraph -> !paragraph.isBlank())
                .map(ArticleRevisionFingerprint::sha256)
                .toList();
        return new ArticleRevisionFingerprint(
                sha256(normalize(article.title())),
                article.publishedAt().toInstant().toString(),
                article.modifiedAt().map(value -> value.toInstant().toString()).orElse(null),
                paragraphHashes
        );
    }

    /** 비교용 공백 정규화 */
    private static String normalize(String value) {
        return WHITESPACE.matcher(value == null ? "" : value).replaceAll(" ").trim();
    }

    /** 고정 길이 SHA-256 Hex 생성 */
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
