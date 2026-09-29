/* 분석 공용 Cache 열람자 식별 */
package com.newsverification.analysiscache.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 기존 비식별 이용량 Key의 단방향 열람자 Digest */
public final class AnalysisCacheViewer {

    private AnalysisCacheViewer() {
    }

    /** 사용자 유형과 정렬된 비식별 Key 조합 */
    public static String fingerprint(String userType, List<String> identifierKeys) {
        if (userType == null || userType.isBlank()
                || identifierKeys == null || identifierKeys.isEmpty()
                || identifierKeys.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Cache viewer identity is required");
        }
        String material = userType.trim() + "\n"
                + String.join("\n", identifierKeys.stream().sorted().toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
