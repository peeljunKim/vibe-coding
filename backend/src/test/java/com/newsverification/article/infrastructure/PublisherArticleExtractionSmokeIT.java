/* 초기 언론사 실제 기사 추출 시험 */
package com.newsverification.article.infrastructure;

import com.newsverification.article.application.ArticleHtmlExtractor;
import com.newsverification.article.application.ArticleHttpClient;
import com.newsverification.article.application.ArticleHttpResponse;
import com.newsverification.article.application.ArticleUrlValidator;
import com.newsverification.article.application.ResolvedArticleUrl;
import com.newsverification.article.application.SafeArticleReader;
import com.newsverification.article.domain.ArticleProcessingException;
import com.newsverification.article.domain.ExtractedArticle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 승인된 외부 요청의 언론사별 추출 결과 기록 */
class PublisherArticleExtractionSmokeIT {

    private static final int REPORT_PREVIEW_CODE_POINTS = 160;
    private static final Pattern POSSIBLE_CONTAMINATION =
            Pattern.compile("광고|댓글|추천\\s*기사|관련\\s*기사|무단\\s*전재|Copyright|ⓒ");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    /** Local 입력 목록의 실제 기사 추출 시험 */
    @Test
    void recordsPublisherExtractionResults() throws IOException {
        Path inputPath = requiredPath("article.smoke.input");
        Path reportPath = requiredPath("article.smoke.report");
        Duration timeout = Duration.ofSeconds(requiredPositiveInt("article.smoke.timeoutSeconds"));
        int maxResponseBytes = requiredPositiveInt("article.smoke.maxResponseBytes");
        int maxRedirects = requiredNonNegativeInt("article.smoke.maxRedirects");
        List<SmokeCase> cases = readCases(inputPath);
        assertThat(cases).as("실제 추출 시험 입력").isNotEmpty();

        var httpClient = new CapturingArticleHttpClient(new ApacheArticleHttpClient());
        var reader = new SafeArticleReader(
                new ArticleUrlValidator(new SystemHostResolver()),
                new ArticleHtmlExtractor(),
                httpClient,
                timeout,
                maxResponseBytes,
                maxRedirects
        );
        List<String> rows = new ArrayList<>();
        rows.add(String.join("\t",
                "publisher",
                "originalUrl",
                "finalUrl",
                "title",
                "publishedAt",
                "modifiedAt",
                "bodyStart",
                "bodyEnd",
                "bodyCodePoints",
                "contamination",
                "dateSources",
                "bodyCandidates",
                "structuredFields",
                "elapsedMs",
                "errorCode"
        ));

        for (SmokeCase smokeCase : cases) {
            rows.add(runCase(reader, httpClient, smokeCase));
        }

        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(reportPath, rows, StandardCharsets.UTF_8);
        assertThat(rows).hasSize(cases.size() + 1);
    }

    /** 단일 후보 기사 시험 */
    private String runCase(
            SafeArticleReader reader,
            CapturingArticleHttpClient httpClient,
            SmokeCase smokeCase
    ) {
        long startedAt = System.nanoTime();
        httpClient.reset();
        try {
            ExtractedArticle article = reader.read(smokeCase.articleUrl(), smokeCase.allowedHosts());
            ArticleHtmlStructureDiagnostics diagnostics = diagnostics(httpClient);
            return row(
                    smokeCase.publisher(),
                    smokeCase.articleUrl(),
                    article.sourceUrl().toString(),
                    article.title(),
                    article.publishedAt().toString(),
                    article.modifiedAt().map(Object::toString).orElse(""),
                    startPreview(article.body()),
                    endPreview(article.body()),
                    Integer.toString(article.body().codePointCount(0, article.body().length())),
                    contamination(article.body()),
                    String.join("|", diagnostics.dateSources()),
                    String.join("|", diagnostics.bodyCandidates()),
                    String.join("|", diagnostics.structuredFields()),
                    elapsedMillis(startedAt),
                    ""
            );
        }
        catch (ArticleProcessingException exception) {
            return failureRow(smokeCase, httpClient, startedAt, exception.error().name());
        }
        catch (RuntimeException exception) {
            return failureRow(smokeCase, httpClient, startedAt, "UNEXPECTED_ERROR");
        }
    }

    /** 실패 결과 행 */
    private String failureRow(
            SmokeCase smokeCase,
            CapturingArticleHttpClient httpClient,
            long startedAt,
            String errorCode
    ) {
        ArticleHtmlStructureDiagnostics diagnostics = diagnostics(httpClient);
        return row(
                smokeCase.publisher(),
                smokeCase.articleUrl(),
                "",
                "",
                "",
                "",
                "",
                "",
                "0",
                "NOT_EVALUATED",
                String.join("|", diagnostics.dateSources()),
                String.join("|", diagnostics.bodyCandidates()),
                String.join("|", diagnostics.structuredFields()),
                elapsedMillis(startedAt),
                errorCode
        );
    }

    /** 마지막 HTML 구조 진단 */
    private ArticleHtmlStructureDiagnostics diagnostics(CapturingArticleHttpClient httpClient) {
        return ArticleHtmlStructureDiagnostics.inspect(httpClient.lastBody());
    }

    /** 본문 혼입 후보 문구 */
    private String contamination(String body) {
        var matcher = POSSIBLE_CONTAMINATION.matcher(body);
        return matcher.find() ? "POSSIBLE_CONTAMINATION:" + matcher.group() : "NOT_DETECTED";
    }

    /** Local TSV 입력 해석 */
    private List<SmokeCase> readCases(Path inputPath) throws IOException {
        List<String> lines = Files.readAllLines(inputPath, StandardCharsets.UTF_8);
        List<SmokeCase> cases = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.startsWith("#") || line.startsWith("publisher\t")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns.length != 3) {
                throw new IllegalArgumentException("Invalid smoke input row: " + (index + 1));
            }
            Set<String> allowedHosts = new LinkedHashSet<>(Arrays.asList(columns[1].split("\\|")));
            if (columns[0].isBlank() || columns[2].isBlank()
                    || allowedHosts.isEmpty() || allowedHosts.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Blank smoke input value at row: " + (index + 1));
            }
            cases.add(new SmokeCase(columns[0], Set.copyOf(allowedHosts), columns[2]));
        }
        return cases;
    }

    /** TSV 안전 문자열 조합 */
    private String row(String... values) {
        return Arrays.stream(values)
                .map(this::singleLine)
                .reduce((left, right) -> left + "\t" + right)
                .orElse("");
    }

    /** 보고서 줄바꿈과 Tab 제거 */
    private String singleLine(String value) {
        String normalized = value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
        return EMAIL.matcher(normalized).replaceAll("[email removed]");
    }

    /** 본문 시작 미리보기 */
    private String startPreview(String body) {
        return codePointSlice(body, 0, Math.min(
                REPORT_PREVIEW_CODE_POINTS,
                body.codePointCount(0, body.length())
        ));
    }

    /** 본문 끝 미리보기 */
    private String endPreview(String body) {
        int length = body.codePointCount(0, body.length());
        return codePointSlice(body, Math.max(0, length - REPORT_PREVIEW_CODE_POINTS), length);
    }

    /** Unicode 글자 기준 부분 문자열 */
    private String codePointSlice(String value, int start, int end) {
        int startIndex = value.offsetByCodePoints(0, start);
        int endIndex = value.offsetByCodePoints(0, end);
        return value.substring(startIndex, endIndex);
    }

    /** 처리 시간 Millisecond 문자열 */
    private String elapsedMillis(long startedAt) {
        return Long.toString(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    /** 필수 경로 설정 */
    private Path requiredPath(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return Path.of(value);
    }

    /** 양수 설정값 */
    private int requiredPositiveInt(String name) {
        int value = requiredNonNegativeInt(name);
        if (value == 0) {
            throw new IllegalStateException(name + " must be positive");
        }
        return value;
    }

    /** 음수 제외 설정값 */
    private int requiredNonNegativeInt(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalStateException(name + " must not be negative");
            }
            return parsed;
        }
        catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be an integer", exception);
        }
    }

    /** 언론사별 시험 입력 */
    private record SmokeCase(String publisher, Set<String> allowedHosts, String articleUrl) {
    }

    /** 기사 전문 비저장 응답 구조 확인 */
    private static final class CapturingArticleHttpClient implements ArticleHttpClient {

        private final ArticleHttpClient delegate;
        private String lastBody = "";

        private CapturingArticleHttpClient(ArticleHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public ArticleHttpResponse get(
                ResolvedArticleUrl target,
                Duration timeout,
                int maxResponseBytes
        ) throws IOException {
            ArticleHttpResponse response = delegate.get(target, timeout, maxResponseBytes);
            lastBody = response.body() == null ? "" : response.body();
            return response;
        }

        /** 단일 시험 응답 초기화 */
        private void reset() {
            lastBody = "";
        }

        /** 마지막 응답 HTML */
        private String lastBody() {
            return lastBody;
        }
    }
}
