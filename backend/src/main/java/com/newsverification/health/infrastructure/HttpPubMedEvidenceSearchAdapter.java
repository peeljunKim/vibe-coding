/* NCBI E-utilities 기반 PubMed 근거 검색 */
package com.newsverification.health.infrastructure;

import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** 영문 검색어를 제한된 PubMed 근거 후보로 변환하는 Adapter */
public final class HttpPubMedEvidenceSearchAdapter implements PubMedEvidenceSearchPort {

    private static final String ESEARCH_URL =
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String EFETCH_URL =
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    private static final String TOOL_NAME = "news_verification";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(350);
    private static final int SEARCH_RESPONSE_LIMIT = 64 * 1024;
    private static final int FETCH_RESPONSE_LIMIT = 1024 * 1024;
    private static final int SUMMARY_LIMIT = 4_000;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:^|\\D)(\\d{4})(?:\\D|$)");

    private final PubMedHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String contactEmail;
    private final String apiKey;
    private final PubMedWaiter waiter;
    private Instant nextRequestAt = Instant.MIN;

    /** 운영 NCBI Client와 연락처 구성 */
    public HttpPubMedEvidenceSearchAdapter(
            ObjectMapper objectMapper,
            Clock clock,
            String contactEmail,
            String apiKey
    ) {
        this(
                new JdkPubMedHttpClient(),
                objectMapper,
                clock,
                contactEmail,
                apiKey,
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    /** 테스트 가능한 HTTP와 대기 경계 구성 */
    HttpPubMedEvidenceSearchAdapter(
            PubMedHttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            String contactEmail,
            String apiKey,
            PubMedWaiter waiter
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.contactEmail = requireContactEmail(contactEmail);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.waiter = Objects.requireNonNull(waiter);
    }

    /** 주장별 ESearch와 PMID 일괄 EFetch 실행 */
    @Override
    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request);
        try {
            var pmids = new LinkedHashSet<String>();
            for (SearchClaim claim : request.claims()) {
                URI searchUri = searchUri(claim.pubMedQuery(), request.maxResultsPerClaim());
                PubMedHttpClient.Response response = get(
                        searchUri,
                        request.deadlineAt(),
                        SEARCH_RESPONSE_LIMIT
                );
                if (!isSuccess(response.statusCode())) {
                    return SearchResponse.temporaryFailure();
                }
                pmids.addAll(readPmids(response.body()));
            }
            if (pmids.isEmpty()) {
                return SearchResponse.noResults();
            }
            PubMedHttpClient.Response response = get(
                    fetchUri(pmids),
                    request.deadlineAt(),
                    FETCH_RESPONSE_LIMIT
            );
            if (!isSuccess(response.statusCode())) {
                return SearchResponse.temporaryFailure();
            }
            List<Evidence> evidences = readEvidence(response.body());
            return evidences.isEmpty()
                    ? SearchResponse.noResults()
                    : SearchResponse.completed(evidences);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SearchResponse.temporaryFailure();
        }
        catch (Exception exception) {
            return SearchResponse.temporaryFailure();
        }
    }

    /** 호출 간격과 남은 Deadline을 적용한 GET */
    private PubMedHttpClient.Response get(URI uri, Instant deadlineAt, int maxResponseBytes)
            throws IOException, InterruptedException {
        waitForPermit(deadlineAt);
        Duration remaining = Duration.between(clock.instant(), deadlineAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IOException("PubMed search deadline exceeded");
        }
        Duration timeout = remaining.compareTo(REQUEST_TIMEOUT) < 0
                ? remaining
                : REQUEST_TIMEOUT;
        return httpClient.get(uri, timeout, maxResponseBytes);
    }

    /** API Key 없는 NCBI 초당 3회 제한 준수 */
    private synchronized void waitForPermit(Instant deadlineAt) throws InterruptedException, IOException {
        Instant now = clock.instant();
        if (nextRequestAt.isAfter(now)) {
            Duration wait = Duration.between(now, nextRequestAt);
            if (!now.plus(wait).isBefore(deadlineAt)) {
                throw new IOException("PubMed search deadline exceeded");
            }
            waiter.sleep(wait);
        }
        nextRequestAt = clock.instant().plus(REQUEST_INTERVAL);
    }

    /** 필터와 호출자 정보를 포함한 ESearch URI */
    private URI searchUri(String query, int maxResults) {
        String filteredQuery = query
                + " AND hasabstract AND humans[mh]"
                + " NOT (retracted publication[pt] OR preprint[pt])";
        return uri(ESEARCH_URL,
                "db", "pubmed",
                "term", filteredQuery,
                "retmax", Integer.toString(maxResults),
                "retmode", "json",
                "sort", "relevance");
    }

    /** 중복 제거 PMID를 포함한 EFetch URI */
    private URI fetchUri(Set<String> pmids) {
        return uri(EFETCH_URL,
                "db", "pubmed",
                "id", String.join(",", pmids),
                "rettype", "abstract",
                "retmode", "xml");
    }

    /** 공통 NCBI Query Parameter 구성 */
    private URI uri(String baseUrl, String... parameters) {
        var query = new ArrayList<String>();
        for (int index = 0; index < parameters.length; index += 2) {
            query.add(encode(parameters[index]) + "=" + encode(parameters[index + 1]));
        }
        query.add("tool=" + encode(TOOL_NAME));
        query.add("email=" + encode(contactEmail));
        if (!apiKey.isEmpty()) {
            query.add("api_key=" + encode(apiKey));
        }
        return URI.create(baseUrl + "?" + String.join("&", query));
    }

    /** ESearch PMID 목록 변환 */
    private List<String> readPmids(String body) throws IOException {
        JsonNode idList = objectMapper.readTree(body).path("esearchresult").path("idlist");
        if (!idList.isArray()) {
            throw new IOException("Invalid PubMed ESearch response");
        }
        var pmids = new ArrayList<String>();
        idList.forEach(node -> {
            String pmid = node.asString();
            if (pmid.matches("\\d+")) {
                pmids.add(pmid);
            }
        });
        return pmids;
    }

    /** EFetch XML의 허용 논문 변환 */
    private List<Evidence> readEvidence(String body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
        NodeList articles = document.getElementsByTagName("PubmedArticle");
        var evidence = new ArrayList<Evidence>();
        for (int index = 0; index < articles.getLength(); index++) {
            Element article = (Element) articles.item(index);
            toEvidence(article).ifPresent(evidence::add);
        }
        evidence.sort(java.util.Comparator.comparingInt(item -> studyPriority(item.studyType())));
        return List.copyOf(evidence);
    }

    /** 단일 PubMed Article의 최소 근거 변환 */
    private Optional<Evidence> toEvidence(Element article) {
        String pmid = firstText(article, "PMID");
        String title = firstText(article, "ArticleTitle");
        String summary = abstractText(article);
        Set<String> publicationTypes = publicationTypes(article);
        if (!pmid.matches("\\d+")
                || title.isBlank()
                || summary.isBlank()
                || containsType(publicationTypes, "retracted publication")
                || containsType(publicationTypes, "preprint")) {
            return Optional.empty();
        }
        Optional<LocalDate> publishedDate = publishedDate(article);
        if (publishedDate.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Evidence(
                pmid,
                title,
                studyType(publicationTypes),
                publishedDate.get(),
                URI.create("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/"),
                limit(summary, SUMMARY_LIMIT)
        ));
    }

    /** 여러 Abstract 절의 순서 보존 결합 */
    private static String abstractText(Element article) {
        NodeList texts = article.getElementsByTagName("AbstractText");
        var sections = new ArrayList<String>();
        for (int index = 0; index < texts.getLength(); index++) {
            String text = texts.item(index).getTextContent().trim();
            if (!text.isBlank()) {
                sections.add(text);
            }
        }
        return String.join(" ", sections);
    }

    /** 연구 유형 우선순위 변환 */
    private static HealthAnalysisResult.EvidenceStudyType studyType(Set<String> types) {
        if (containsType(types, "guideline") || containsType(types, "practice guideline")) {
            return HealthAnalysisResult.EvidenceStudyType.GUIDELINE;
        }
        if (containsType(types, "systematic review")) {
            return HealthAnalysisResult.EvidenceStudyType.SYSTEMATIC_REVIEW;
        }
        if (containsType(types, "meta-analysis")) {
            return HealthAnalysisResult.EvidenceStudyType.META_ANALYSIS;
        }
        if (containsType(types, "randomized controlled trial")
                || containsType(types, "clinical trial")) {
            return HealthAnalysisResult.EvidenceStudyType.RANDOMIZED_TRIAL;
        }
        if (containsType(types, "observational study")) {
            return HealthAnalysisResult.EvidenceStudyType.OBSERVATIONAL;
        }
        return HealthAnalysisResult.EvidenceStudyType.OTHER;
    }

    /** 요구사항 기준 연구 유형 우선순위 */
    private static int studyPriority(HealthAnalysisResult.EvidenceStudyType studyType) {
        return switch (studyType) {
            case GUIDELINE -> 0;
            case SYSTEMATIC_REVIEW, META_ANALYSIS -> 1;
            case RANDOMIZED_TRIAL -> 2;
            case OBSERVATIONAL -> 3;
            case OTHER -> 4;
        };
    }

    /** PublicationType 소문자 집합 */
    private static Set<String> publicationTypes(Element article) {
        NodeList nodes = article.getElementsByTagName("PublicationType");
        var types = new LinkedHashSet<String>();
        for (int index = 0; index < nodes.getLength(); index++) {
            types.add(nodes.item(index).getTextContent().trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(types);
    }

    /** 발행일의 안전한 최소 단위 변환 */
    private static Optional<LocalDate> publishedDate(Element article) {
        NodeList dates = article.getElementsByTagName("PubDate");
        if (dates.getLength() == 0) {
            return Optional.empty();
        }
        Element date = (Element) dates.item(0);
        String yearText = firstText(date, "Year");
        if (yearText.isBlank()) {
            var matcher = YEAR_PATTERN.matcher(firstText(date, "MedlineDate"));
            if (!matcher.find()) {
                return Optional.empty();
            }
            yearText = matcher.group(1);
        }
        try {
            int year = Integer.parseInt(yearText);
            int month = parseMonth(firstText(date, "Month"));
            int day = parsePositive(firstText(date, "Day"), 1);
            return Optional.of(LocalDate.of(year, month, day));
        }
        catch (DateTimeException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** 숫자·영문 월 변환 */
    private static int parseMonth(String value) {
        if (value.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ignored) {
            return Arrays.stream(Month.values())
                    .filter(month -> month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                            .equalsIgnoreCase(value))
                    .map(Month::getValue)
                    .findFirst()
                    .orElse(1);
        }
    }

    /** 양의 정수 또는 기본값 */
    private static int parsePositive(String value, int fallback) {
        if (value.isBlank()) {
            return fallback;
        }
        int parsed = Integer.parseInt(value);
        return parsed > 0 ? parsed : fallback;
    }

    /** 첫 번째 하위 Tag 문자열 */
    private static String firstText(Element root, String tagName) {
        NodeList nodes = root.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        Node node = nodes.item(0);
        return node == null ? "" : node.getTextContent().trim();
    }

    /** 유형 포함 여부 */
    private static boolean containsType(Set<String> types, String expected) {
        return types.contains(expected);
    }

    /** 문자열 길이 제한 */
    private static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 성공 상태 확인 */
    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /** Query Parameter 인코딩 */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** NCBI 연락처 형식 검증 */
    private static String requireContactEmail(String value) {
        String email = value == null ? "" : value.trim();
        if (email.isEmpty() || !email.contains("@")) {
            throw new IllegalArgumentException("PubMed contact email is required");
        }
        return email;
    }

    /** NCBI 호출 간격 대기 경계 */
    @FunctionalInterface
    interface PubMedWaiter {
        void sleep(Duration duration) throws InterruptedException;
    }
}
