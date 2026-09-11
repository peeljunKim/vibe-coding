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

/** 구조화 Metadata와 본문 Container 기반 기사 정제 */
public final class ArticleHtmlExtractor {

    private static final int MAX_BODY_CODE_POINTS = 20_000;
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");
    private static final String UNWANTED_ELEMENTS = String.join(", ",
            "script", "style", "noscript", "nav", "aside", "footer", "form", "button",
            "[aria-hidden=true]", ".advertisement", ".ad", ".ads", ".comment", ".comments",
            ".recommend", ".related"
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
                        .or(() -> firstAttribute(document, "time[datetime]", "datetime")),
                ArticleProcessingError.MISSING_PUBLISHED_AT
        );
        Optional<OffsetDateTime> modifiedAt = optionalDate(
                firstAttribute(document, "meta[property=article:modified_time]", "content")
                        .or(() -> firstAttribute(document, "meta[name=article:modified_time]", "content"))
        );

        Element bodyElement = Optional.ofNullable(document.selectFirst("[itemprop=articleBody]"))
                .orElseGet(() -> document.selectFirst("article"));
        if (bodyElement == null) {
            throw new ArticleProcessingException(ArticleProcessingError.MISSING_BODY);
        }
        bodyElement.select(UNWANTED_ELEMENTS).remove();

        String body = bodyElement.select("p").stream()
                .map(Element::text)
                .map(ArticleHtmlExtractor::normalizeText)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElseGet(() -> normalizeText(bodyElement.text()));
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
