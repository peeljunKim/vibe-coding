/* 분석 공용 Cache Key 생성 */
package com.newsverification.analysiscache.application;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** 외부 요청 없는 URL 정규화와 Version Digest 생성 */
public final class AnalysisCacheKeyFactory {

    private AnalysisCacheKeyFactory() {
    }

    /** 건강 분석 Cache Key 생성 */
    public static Optional<AnalysisCacheKey> health(
            String rawUrl,
            AnalysisCacheVersions versions
    ) {
        Objects.requireNonNull(versions);
        return normalizedUrl(rawUrl).map(url -> key(
                AnalysisCacheFeature.HEALTH,
                url,
                versions.healthModelVersion(),
                versions.healthPolicyVersion(),
                versions.healthEvidenceAllowlistVersion(),
                versions.publisherPolicyVersion()
        ));
    }

    /** 제목 분석 Cache Key 생성 */
    public static Optional<AnalysisCacheKey> headline(
            String rawUrl,
            AnalysisCacheVersions versions
    ) {
        Objects.requireNonNull(versions);
        return normalizedUrl(rawUrl).map(url -> key(
                AnalysisCacheFeature.HEADLINE,
                url,
                versions.headlineModelVersion(),
                versions.headlinePolicyVersion(),
                versions.publisherPolicyVersion()
        ));
    }

    /** 기능·URL·Version 조합의 SHA-256 변환 */
    private static AnalysisCacheKey key(
            AnalysisCacheFeature feature,
            String normalizedUrl,
            String... versions
    ) {
        String material = feature.name() + "\n" + normalizedUrl + "\n"
                + String.join("\n", versions);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new AnalysisCacheKey(feature, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** HTTPS URL의 비교용 정규화 */
    private static Optional<String> normalizedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(rawUrl.trim()).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return Optional.empty();
            }
            String host = IDN.toASCII(uri.getHost(), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty()
                    ? "/"
                    : uri.getRawPath();
            return Optional.of("https://" + host + path
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
