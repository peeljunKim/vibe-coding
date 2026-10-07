/* 기사 원문 없는 HTML 구조 진단 */
package com.newsverification.article.infrastructure;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 날짜와 본문 후보 구조 요약 */
record ArticleHtmlStructureDiagnostics(
        List<String> dateSources,
        List<String> bodyCandidates,
        List<String> structuredFields
) {

    private static final Pattern DATE_HINT = Pattern.compile(
            "date|publish|created|modified|updated|reg",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BODY_HINT = Pattern.compile(
            "article|body|content|view|news|story",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_TYPE_ARRAY = Pattern.compile("\"@type\"\\s*:\\s*\\[");
    private static final Pattern JSON_TYPE_TEXT = Pattern.compile("\"@type\"\\s*:\\s*\"");
    private static final Pattern JSON_PUBLISHED_AT = Pattern.compile(
            "\"datePublished\"\\s*:\\s*\"([^\"\\r\\n]{1,64})\""
    );
    private static final int MAX_BODY_CANDIDATES = 5;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** HTML 구조 요약 생성 */
    static ArticleHtmlStructureDiagnostics inspect(String html) {
        var document = Jsoup.parse(html == null ? "" : html);
        Set<String> dates = new LinkedHashSet<>();
        document.select("meta[property], meta[name], meta[itemprop]").forEach(element -> {
            addDateSource(dates, element, "property");
            addDateSource(dates, element, "name");
            addDateSource(dates, element, "itemprop");
        });
        if (!document.select("time[datetime]").isEmpty()) {
            dates.add("time[datetime]");
        }

        List<String> bodyCandidates = document.select("[id], [class]").stream()
                .filter(ArticleHtmlStructureDiagnostics::isBodyCandidate)
                .sorted(Comparator.comparingInt(ArticleHtmlStructureDiagnostics::textLength).reversed())
                .limit(MAX_BODY_CANDIDATES)
                .map(ArticleHtmlStructureDiagnostics::describeBodyCandidate)
                .toList();

        Set<String> structuredFields = new LinkedHashSet<>();
        document.select("script[type=application/ld+json]").forEach(script -> {
            String data = script.data();
            try {
                JSON_MAPPER.readTree(data);
                structuredFields.add("json=valid");
            }
            catch (JacksonException ignored) {
                structuredFields.add("json=invalid");
            }
            if (JSON_TYPE_ARRAY.matcher(data).find()) {
                structuredFields.add("@type=array");
            }
            else if (JSON_TYPE_TEXT.matcher(data).find()) {
                structuredFields.add("@type=text");
            }
            if (containsJsonString(data, "@type", "NewsArticle")) {
                structuredFields.add("NewsArticle");
            }
            else if (data.contains("\"NewsArticle\"")) {
                structuredFields.add("NewsArticle");
            }
            addJsonField(structuredFields, data, "headline");
            addJsonField(structuredFields, data, "datePublished");
            var publishedAt = JSON_PUBLISHED_AT.matcher(data);
            if (publishedAt.find()) {
                structuredFields.add("datePublished=" + publishedAt.group(1));
            }
            addJsonField(structuredFields, data, "articleBody");
        });

        return new ArticleHtmlStructureDiagnostics(
                List.copyOf(dates),
                bodyCandidates,
                List.copyOf(structuredFields)
        );
    }

    /** 날짜 Metadata 위치 추가 */
    private static void addDateSource(Set<String> sources, Element element, String attribute) {
        String value = element.attr(attribute);
        if (!value.isBlank() && DATE_HINT.matcher(value).find()) {
            sources.add("meta[" + attribute + "=" + safeToken(value) + "]");
        }
    }

    /** 본문 후보 여부 */
    private static boolean isBodyCandidate(Element element) {
        String marker = element.id() + " " + element.className();
        return BODY_HINT.matcher(marker).find() && !element.select("p").isEmpty();
    }

    /** 본문 후보 요약 */
    private static String describeBodyCandidate(Element element) {
        StringBuilder label = new StringBuilder(element.tagName());
        if (!element.id().isBlank()) {
            label.append('#').append(safeToken(element.id()));
        }
        for (String className : element.classNames()) {
            label.append('.').append(safeToken(className));
        }
        return label + "[p=" + element.select("p").size()
                + ",text=" + textLength(element) + "]";
    }

    /** 본문 후보 글자 수 */
    private static int textLength(Element element) {
        String text = element.text();
        return text.codePointCount(0, text.length());
    }

    /** JSON-LD 값 확인 */
    private static boolean containsJsonString(String json, String key, String value) {
        String normalized = json.replaceAll("\\s+", "");
        return normalized.contains("\"" + key + "\":\"" + value + "\"");
    }

    /** JSON-LD 필드 확인 */
    private static void addJsonField(Set<String> fields, String json, String field) {
        if (json.contains("\"" + field + "\"")) {
            fields.add(field);
        }
    }

    /** 보고서용 식별자 정제 */
    private static String safeToken(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
