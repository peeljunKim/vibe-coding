/* 기사 HTML 필수 정보와 본문 추출 */
package com.newsverification.article.application;

import com.newsverification.article.domain.ArticleProcessingError;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 구조화 Metadata와 본문 Container 기반 기사 정제 */
public final class ArticleHtmlExtractor {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final int MAX_BODY_CODE_POINTS = 20_000;
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");
    private static final Pattern NEWS_ARTICLE_JSON_TYPE = Pattern.compile(
            "\"@type\"\\s*:\\s*(?:\"NewsArticle\"|\\[[^\\]]{0,256}\"NewsArticle\"[^\\]]{0,256}\\])"
    );
    private static final Pattern JSON_LD_PUBLISHED_AT = Pattern.compile(
            "\"datePublished\"\\s*:\\s*\"([^\"\\r\\n]{1,64})\""
    );
    private static final Pattern KOREAN_LOCAL_DATE_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"
    );
    private static final Pattern INLINE_BOILERPLATE = Pattern.compile(
            "구글에서\\s*(?:선호하는 매체로 추가|서울신문 먼저 보기)|이미지 확대|(?<!\\S)닫기(?!\\S)"
    );
    private static final Pattern TRAILING_BOILERPLATE = Pattern.compile(
            "\\s*(?:■\\s*제보하기|※\\s*['‘’\"]?당신의 제보가 뉴스가 됩니다"
                    + "|◎\\s*공감언론\\s+뉴시스|GoodNews\\s+paper\\s*ⓒ"
                    + "|Copyright\\s*[©ⓒ])[\\s\\S]*$"
    );
    private static final String UNWANTED_ELEMENTS = String.join(", ",
            "script", "style", "noscript", "nav", "aside", "footer", "form", "button",
            "[aria-hidden=true]", ".advertisement", ".ad", ".ads", ".comment", ".comments",
            ".recommend", ".related", ".news_relArt", ".news_copyright", ".copyright"
    );

    /** 기사 필수 정보와 정제 본문 생성 */
    public ExtractedArticle extract(URI sourceUrl, String html) {
        Document document = Jsoup.parse(html, sourceUrl.toString());
        String title = requiredText(
                firstAttribute(document, "meta[property=og:title]", "content")
                        .or(() -> firstAttribute(document, "meta[name=twitter:title]", "content"))
                        .or(() -> firstText(document, "h1")),
                ArticleProcessingError.MISSING_TITLE
        );
        OffsetDateTime publishedAt = requiredDate(
                firstAttribute(document, "meta[property=article:published_time]", "content")
                        .or(() -> firstAttribute(document, "meta[name=article:published_time]", "content"))
                        .or(() -> firstAttribute(document, "time[datetime]", "datetime"))
                        .or(() -> structuredArticleValue(document, "datePublished")),
                ArticleProcessingError.MISSING_PUBLISHED_AT
        );
        Optional<OffsetDateTime> modifiedAt = optionalDate(
                firstAttribute(document, "meta[property=article:modified_time]", "content")
                        .or(() -> firstAttribute(document, "meta[name=article:modified_time]", "content"))
        );

        Element bodyElement = firstBodyElement(document);
        if (bodyElement == null) {
            throw new ArticleProcessingException(ArticleProcessingError.MISSING_BODY);
        }
        bodyElement.select(UNWANTED_ELEMENTS).remove();

        String paragraphBody = bodyElement.select("p").stream()
                .map(Element::text)
                .map(ArticleHtmlExtractor::normalizeText)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String fullBody = normalizeText(bodyElement.text());
        String body = useFullBody(paragraphBody, fullBody) ? fullBody : paragraphBody;
        body = INLINE_BOILERPLATE.matcher(body).replaceAll(" ");
        body = TRAILING_BOILERPLATE.matcher(body).replaceFirst("").trim();
        if (body.isBlank()) {
            throw new ArticleProcessingException(ArticleProcessingError.MISSING_BODY);
        }
        if (body.codePointCount(0, body.length()) > MAX_BODY_CODE_POINTS) {
            throw new ArticleProcessingException(ArticleProcessingError.ARTICLE_TOO_LONG);
        }
        if (!KOREAN.matcher(title + " " + body).find()) {
            throw new ArticleProcessingException(ArticleProcessingError.NON_KOREAN_ARTICLE);
        }

        return new ExtractedArticle(sourceUrl, title, body, publishedAt, modifiedAt);
    }

    /** Selector Attribute 첫 값 조회 */
    private static Optional<String> firstAttribute(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return Optional.empty();
        }
        String value = normalizeText(element.attr(attribute));
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** Selector Text 첫 값 조회 */
    private static Optional<String> firstText(Document document, String selector) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return Optional.empty();
        }
        String value = normalizeText(element.text());
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** 기사 본문 Container 조회 */
    private static Element firstBodyElement(Document document) {
        for (String selector : new String[]{
                "[itemprop=articleBody]",
                "#article-view-content",
                ".view-article",
                ".viewer",
                "#CmAdContent",
                "section.news_view",
                ".viewContent",
                ".news_body",
                ".entry-content",
                ".view_cont",
                "article"
        }) {
            Element element = document.selectFirst(selector);
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    /** 문단 밖 본문 포함 여부 판정 */
    private static boolean useFullBody(String paragraphBody, String fullBody) {
        if (paragraphBody.isBlank()) {
            return true;
        }
        int paragraphLength = paragraphBody.codePointCount(0, paragraphBody.length());
        int fullLength = fullBody.codePointCount(0, fullBody.length());
        return paragraphLength * 2 < fullLength;
    }

    /** NewsArticle 구조화 필드 첫 값 조회 */
    private static Optional<String> structuredArticleValue(Document document, String field) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            String data = script.data();
            try {
                Optional<String> value = findNewsArticleValue(JSON_MAPPER.readTree(data), field);
                if (value.isPresent()) {
                    return Optional.of(normalizeStructuredValue(field, value.get()));
                }
            }
            catch (JacksonException ignored) {
                // 다른 구조화 Metadata 확인 계속
            }
            Optional<String> fallback = rawNewsArticleValue(data, field);
            if (fallback.isPresent()) {
                return Optional.of(normalizeStructuredValue(field, fallback.get()));
            }
        }
        return Optional.empty();
    }

    /** 비표준 NewsArticle 게시일 제한 추출 */
    private static Optional<String> rawNewsArticleValue(String data, String field) {
        if (!"datePublished".equals(field) || !NEWS_ARTICLE_JSON_TYPE.matcher(data).find()) {
            return Optional.empty();
        }
        var matcher = JSON_LD_PUBLISHED_AT.matcher(data);
        return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
    }

    /** 국내 기사 Offset 없는 게시일 보정 */
    private static String normalizeStructuredValue(String field, String value) {
        if ("datePublished".equals(field) && KOREAN_LOCAL_DATE_TIME.matcher(value).matches()) {
            return value.replace(' ', 'T') + "+09:00";
        }
        return value;
    }

    /** NewsArticle Node 재귀 조회 */
    private static Optional<String> findNewsArticleValue(JsonNode node, String field) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.isObject()) {
            JsonNode type = node.get("@type");
            JsonNode value = node.get(field);
            if (isNewsArticleType(type)
                    && value != null && value.isTextual() && !value.textValue().isBlank()) {
                return Optional.of(value.textValue().trim());
            }
            for (JsonNode child : node) {
                Optional<String> nested = findNewsArticleValue(child, field);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> nested = findNewsArticleValue(child, field);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        return Optional.empty();
    }

    /** NewsArticle 타입 포함 여부 */
    private static boolean isNewsArticleType(JsonNode type) {
        if (type == null) {
            return false;
        }
        if (type.isTextual()) {
            return "NewsArticle".equals(type.textValue());
        }
        if (type.isArray()) {
            for (JsonNode item : type) {
                if (item.isTextual() && "NewsArticle".equals(item.textValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 필수 문자열 확인 */
    private static String requiredText(Optional<String> value, ArticleProcessingError error) {
        return value.orElseThrow(() -> new ArticleProcessingException(error));
    }

    /** 필수 게시 시각 변환 */
    private static OffsetDateTime requiredDate(Optional<String> value, ArticleProcessingError missingError) {
        String rawDate = value.orElseThrow(() -> new ArticleProcessingException(missingError));
        try {
            return OffsetDateTime.parse(rawDate);
        }
        catch (DateTimeParseException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_PUBLISHED_AT, exception);
        }
    }

    /** 선택 수정 시각 변환 */
    private static Optional<OffsetDateTime> optionalDate(Optional<String> value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(value.get()));
        }
        catch (DateTimeParseException exception) {
            throw new ArticleProcessingException(ArticleProcessingError.INVALID_MODIFIED_AT, exception);
        }
    }

    /** 화면용 공백 정규화 */
    private static String normalizeText(String value) {
        return WHITESPACE.matcher(value == null ? "" : value).replaceAll(" ").trim();
    }
}
