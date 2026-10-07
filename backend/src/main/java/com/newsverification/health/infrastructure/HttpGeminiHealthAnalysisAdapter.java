/* Gemini 기반 건강 기사 구조화 분석 */
package com.newsverification.health.infrastructure;

import com.newsverification.article.domain.ExtractedArticle;
import com.newsverification.health.application.HealthAnalysisPort;
import com.newsverification.health.application.HealthAnalysisResult;
import com.newsverification.health.application.PubMedEvidenceSearchPort;
import com.newsverification.health.application.PubMedEvidenceSearchService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 최소 기사 입력과 허용 근거만 전송하는 Gemini Adapter */
public final class HttpGeminiHealthAnalysisAdapter implements HealthAnalysisPort {

    private static final String ENDPOINT_PREFIX =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final int MAX_ARTICLE_EXCERPT_CHARS = 6_000;
    private static final int MAX_EVIDENCE_TITLE_CHARS = 300;
    private static final int MAX_EVIDENCE_SUMMARY_CHARS = 1_000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_REASON_CHARS = 1_000;
    private static final int MAX_CLAIM_CHARS = 500;
    private static final int MAX_EVIDENCES_PER_CLAIM = 5;

    private final GeminiHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PubMedEvidenceSearchService pubMedSearchService;
    private final String model;
    private final String apiKey;
    private final String policyVersion;
    private final String evidenceAllowlistVersion;
    private final URI endpoint;

    /** 운영 Gemini HTTP Client 구성 */
    public HttpGeminiHealthAnalysisAdapter(
            ObjectMapper objectMapper,
            Clock clock,
            PubMedEvidenceSearchService pubMedSearchService,
            String model,
            String apiKey,
            String policyVersion,
            String evidenceAllowlistVersion
    ) {
        this(
                new JdkGeminiHttpClient(),
                objectMapper,
                clock,
                pubMedSearchService,
                model,
                apiKey,
                policyVersion,
                evidenceAllowlistVersion
        );
    }

    /** Fixture HTTP Client 포함 구성 */
    HttpGeminiHealthAnalysisAdapter(
            GeminiHttpClient httpClient,
            ObjectMapper objectMapper,
            Clock clock,
            PubMedEvidenceSearchService pubMedSearchService,
            String model,
            String apiKey,
            String policyVersion,
            String evidenceAllowlistVersion
    ) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.pubMedSearchService = Objects.requireNonNull(pubMedSearchService);
        this.model = requireModel(model);
        this.apiKey = requireText(apiKey, 1_000, "Gemini API key");
        this.policyVersion = requireText(policyVersion, 100, "Health policy version");
        this.evidenceAllowlistVersion = requireText(
                evidenceAllowlistVersion,
                100,
                "Evidence allowlist version"
        );
        this.endpoint = URI.create(ENDPOINT_PREFIX + this.model + ":generateContent");
    }

    /** 주장 추출·근거 검색·근거 판정의 제한 실행 */
    @Override
    public HealthAnalysisResult analyze(ExtractedArticle article, Instant deadlineAt) {
        Objects.requireNonNull(article);
        Objects.requireNonNull(deadlineAt);
        ensureBeforeDeadline(deadlineAt);

        List<ClaimDraft> claims = extractClaims(article, deadlineAt);
        PubMedEvidenceSearchPort.SearchResponse searchResponse = pubMedSearchService.search(
                new PubMedEvidenceSearchPort.SearchRequest(
                        claims.stream()
                                .map(claim -> new PubMedEvidenceSearchPort.SearchClaim(
                                        claim.claim(),
                                        claim.pubMedQuery()
                                ))
                                .toList(),
                        MAX_EVIDENCES_PER_CLAIM,
                        deadlineAt
                )
        );
        if (searchResponse.status() == PubMedEvidenceSearchPort.SearchStatus.TEMPORARY_FAILURE) {
            throw new IllegalStateException("PubMed evidence search temporarily unavailable");
        }
        if (searchResponse.status() == PubMedEvidenceSearchPort.SearchStatus.NO_RESULTS
                || searchResponse.evidences().isEmpty()) {
            return insufficientResult(article, claims);
        }
        List<ClaimDecision> decisions = decideClaims(
                claims,
                searchResponse.evidences(),
                deadlineAt
        );
        return toResult(article, claims, decisions, searchResponse.evidences());
    }

    /** 제한된 기사 앞부분 기반 핵심 주장 추출 */
    private List<ClaimDraft> extractClaims(ExtractedArticle article, Instant deadlineAt) {
        String excerpt = truncate(normalizeWhitespace(article.body()), MAX_ARTICLE_EXCERPT_CHARS);
        var input = new LinkedHashMap<String, Object>();
        input.put("title", article.title());
        input.put("articleExcerpt", excerpt);
        String prompt = """
                공개 한국어 건강 기사에서 검증 가능한 의학 주장을 중요도순 최대 3개 추출하세요.
                각 주장에는 기사에 실제로 있는 한국어 주장과 ASCII PubMed 검색어만 작성하세요.
                PubMed 검색어는 의학 개념 2~4개를 AND로 연결하고 결론을 유도하는 표현을 제외하세요.
                기사에 없는 사실이나 사용자 정보를 추가하지 마세요.
                INPUT_JSON:
                """ + writeJson(input);
        ClaimExtraction response = execute(
                prompt,
                claimExtractionSchema(),
                ClaimExtraction.class,
                deadlineAt
        );
        return validateClaims(response);
    }

    /** 최소 PubMed 메타데이터 기반 주장 판정 */
    private List<ClaimDecision> decideClaims(
            List<ClaimDraft> claims,
            List<PubMedEvidenceSearchPort.Evidence> evidences,
            Instant deadlineAt
    ) {
        var input = new LinkedHashMap<String, Object>();
        input.put("claims", claims.stream().map(claim -> Map.of(
                "order", claim.order(),
                "claim", claim.claim()
        )).toList());
        input.put("pubMedEvidence", evidences.stream().map(evidence -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("pmid", evidence.pmid());
            value.put("studyType", evidence.studyType().name());
            value.put("title", truncate(evidence.title(), MAX_EVIDENCE_TITLE_CHARS));
            value.put("publishedDate", evidence.publishedDate().toString());
            value.put("summary", truncate(evidence.summary(), MAX_EVIDENCE_SUMMARY_CHARS));
            return value;
        }).toList());
        String prompt = """
                제공된 주장과 PubMed 근거만 비교하세요. 사전 지식으로 근거를 보충하지 마세요.
                각 주장에 상태, 쉬운 한국어 이유, 실제 사용한 PMID와 관계를 작성하세요.
                근거가 없거나 부족하면 INSUFFICIENT 또는 NEEDS_REVIEW를 사용하세요.
                INPUT_JSON:
                """ + writeJson(input);
        ClaimAssessment response = execute(
                prompt,
                claimAssessmentSchema(),
                ClaimAssessment.class,
                deadlineAt
        );
        return validateDecisions(response, claims, evidences);
    }

    /** Gemini 구조화 요청과 후보 응답 변환 */
    private <T> T execute(
            String prompt,
            Map<String, Object> schema,
            Class<T> responseType,
            Instant deadlineAt
    ) {
        Duration remaining = remaining(deadlineAt);
        var generationConfig = new LinkedHashMap<String, Object>();
        generationConfig.put("temperature", 0);
        generationConfig.put("maxOutputTokens", 8_192);
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "low"));
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseJsonSchema", schema);
        var requestValue = new LinkedHashMap<String, Object>();
        requestValue.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", prompt))
        )));
        requestValue.put("generationConfig", generationConfig);
        GeminiHttpClient.Response response;
        try {
            response = httpClient.post(new GeminiHttpClient.Request(
                    endpoint,
                    apiKey,
                    writeJson(requestValue),
                    remaining,
                    MAX_RESPONSE_BYTES
            ));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini analysis interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Gemini analysis temporarily unavailable", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Gemini analysis temporarily unavailable");
        }
        try {
            String candidateText = candidateText(objectMapper.readTree(response.body()));
            return objectMapper.readValue(candidateText, responseType);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Gemini structured response", exception);
        }
    }

    /** 단일 정상 종료 후보의 JSON Text 추출 */
    private String candidateText(JsonNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Gemini candidate is required");
        }
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.size() != 1) {
            throw new IllegalArgumentException("Gemini candidate is required");
        }
        JsonNode candidate = candidates.get(0);
        JsonNode finishReason = candidate.get("finishReason");
        JsonNode content = candidate.get("content");
        JsonNode parts = content == null ? null : content.get("parts");
        if (finishReason == null || !"STOP".equals(finishReason.stringValue())
                || parts == null || !parts.isArray() || parts.size() != 1
                || parts.get(0).get("text") == null) {
            throw new IllegalArgumentException("Gemini candidate did not finish safely");
        }
        return requireText(
                parts.get(0).get("text").stringValue(),
                MAX_RESPONSE_BYTES,
                "Gemini text"
        );
    }

    /** 주장 추출 응답의 순서·길이·검색어 검증 */
    private List<ClaimDraft> validateClaims(ClaimExtraction response) {
        if (response == null || response.claims() == null
                || response.claims().isEmpty() || response.claims().size() > 3) {
            throw new IllegalStateException("Invalid Gemini claim response");
        }
        var validated = new ArrayList<ClaimDraft>();
        for (int index = 0; index < response.claims().size(); index++) {
            ClaimDraft claim = response.claims().get(index);
            if (claim == null || claim.order() != index + 1) {
                throw new IllegalStateException("Invalid Gemini claim order");
            }
            String text = requireText(claim.claim(), MAX_CLAIM_CHARS, "Gemini claim");
            String query = requireText(claim.pubMedQuery(), 300, "PubMed query");
            if (query.chars().anyMatch(character -> character > 127)) {
                throw new IllegalStateException("Invalid Gemini PubMed query");
            }
            validated.add(new ClaimDraft(claim.order(), text, query));
        }
        return List.copyOf(validated);
    }

    /** 판정 응답의 주장·근거 참조 검증 */
    private List<ClaimDecision> validateDecisions(
            ClaimAssessment response,
            List<ClaimDraft> claims,
            List<PubMedEvidenceSearchPort.Evidence> evidences
    ) {
        if (response == null || response.decisions() == null
                || response.decisions().size() != claims.size()) {
            throw new IllegalStateException("Invalid Gemini decision response");
        }
        Set<String> allowedPmids = evidences.stream()
                .map(PubMedEvidenceSearchPort.Evidence::pmid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var validated = new ArrayList<ClaimDecision>();
        for (int index = 0; index < response.decisions().size(); index++) {
            ClaimDecision decision = response.decisions().get(index);
            if (decision == null || decision.order() != index + 1
                    || decision.evidences() == null
                    || decision.evidences().size() > MAX_EVIDENCES_PER_CLAIM) {
                throw new IllegalStateException("Invalid Gemini decision order");
            }
            HealthAnalysisResult.ClaimStatus.valueOf(decision.status());
            String reason = requireText(decision.reason(), MAX_REASON_CHARS, "Gemini reason");
            var seenPmids = new LinkedHashSet<String>();
            var evidenceDecisions = new ArrayList<EvidenceDecision>();
            for (EvidenceDecision evidence : decision.evidences()) {
                if (evidence == null || !allowedPmids.contains(evidence.pmid())
                        || !seenPmids.add(evidence.pmid())) {
                    throw new IllegalStateException("Invalid Gemini evidence reference");
                }
                HealthAnalysisResult.EvidenceRelationType.valueOf(evidence.relationType());
                evidenceDecisions.add(new EvidenceDecision(
                        evidence.pmid(),
                        evidence.relationType(),
                        requireText(evidence.summary(), MAX_REASON_CHARS, "Gemini evidence summary"),
                        optionalText(evidence.conflictDescription(), MAX_REASON_CHARS)
                ));
            }
            validated.add(new ClaimDecision(
                    decision.order(),
                    decision.status(),
                    reason,
                    List.copyOf(evidenceDecisions)
            ));
        }
        return List.copyOf(validated);
    }

    /** 검증된 판정과 서버 고정 규칙의 Domain 결과 생성 */
    private HealthAnalysisResult toResult(
            ExtractedArticle article,
            List<ClaimDraft> claims,
            List<ClaimDecision> decisions,
            List<PubMedEvidenceSearchPort.Evidence> evidences
    ) {
        Map<String, PubMedEvidenceSearchPort.Evidence> evidenceByPmid = new HashMap<>();
        evidences.forEach(evidence -> evidenceByPmid.putIfAbsent(evidence.pmid(), evidence));
        List<HealthAnalysisResult.Claim> resultClaims = new ArrayList<>();
        for (int index = 0; index < claims.size(); index++) {
            ClaimDraft claim = claims.get(index);
            ClaimDecision decision = decisions.get(index);
            List<HealthAnalysisResult.Evidence> claimEvidences = decision.evidences().stream()
                    .map(evidence -> toEvidence(evidence, evidenceByPmid.get(evidence.pmid())))
                    .toList();
            HealthAnalysisResult.ClaimStatus status = normalizedStatus(
                    HealthAnalysisResult.ClaimStatus.valueOf(decision.status()),
                    claimEvidences
            );
            resultClaims.add(new HealthAnalysisResult.Claim(
                    claim.order(),
                    claim.claim(),
                    status,
                    decision.reason(),
                    claimEvidences
            ));
        }
        return result(article, resultClaims);
    }

    /** 검색 결과 없음의 AI 추가 호출 없는 결과 */
    private HealthAnalysisResult insufficientResult(
            ExtractedArticle article,
            List<ClaimDraft> claims
    ) {
        List<HealthAnalysisResult.Claim> resultClaims = claims.stream()
                .map(claim -> new HealthAnalysisResult.Claim(
                        claim.order(),
                        claim.claim(),
                        HealthAnalysisResult.ClaimStatus.INSUFFICIENT,
                        "확인 가능한 PubMed 근거를 찾지 못했습니다.",
                        List.of()
                ))
                .toList();
        return result(article, resultClaims);
    }

    /** 서버 계산값과 기사 Metadata 결합 */
    private HealthAnalysisResult result(
            ExtractedArticle article,
            List<HealthAnalysisResult.Claim> claims
    ) {
        int confirmed = (int) claims.stream()
                .filter(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.SUPPORTED)
                .count();
        BigDecimal confirmationRate = BigDecimal.valueOf(confirmed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(claims.size()), 2, RoundingMode.HALF_UP);
        return new HealthAnalysisResult(
                new HealthAnalysisResult.ArticleSummary(
                        article.sourceUrl(),
                        article.title(),
                        article.sourceUrl().getHost(),
                        article.publishedAt(),
                        article.modifiedAt().orElse(null)
                ),
                clock.instant(),
                overallStatus(claims),
                confirmationRate,
                confirmed,
                claims.size(),
                claims,
                HealthAnalysisResult.ExpertReviewStatus.NOT_REVIEWED,
                model,
                policyVersion,
                evidenceAllowlistVersion,
                claims.stream().anyMatch(claim ->
                        claim.status() == HealthAnalysisResult.ClaimStatus.NEEDS_REVIEW
                                || claim.status() == HealthAnalysisResult.ClaimStatus.INSUFFICIENT)
        );
    }

    /** 알려진 PubMed 근거만 Domain 근거로 변환 */
    private HealthAnalysisResult.Evidence toEvidence(
            EvidenceDecision decision,
            PubMedEvidenceSearchPort.Evidence evidence
    ) {
        return new HealthAnalysisResult.Evidence(
                HealthAnalysisResult.EvidenceSourceKind.PUBMED,
                evidence.pmid(),
                evidence.studyType(),
                HealthAnalysisResult.EvidenceRelationType.valueOf(decision.relationType()),
                evidence.title(),
                "PubMed",
                evidence.publishedDate(),
                evidence.sourceUrl(),
                decision.summary(),
                decision.conflictDescription()
        );
    }

    /** 자료 수량에 따른 과도한 근거 있음 판정 완화 */
    private HealthAnalysisResult.ClaimStatus normalizedStatus(
            HealthAnalysisResult.ClaimStatus requested,
            List<HealthAnalysisResult.Evidence> evidences
    ) {
        if (evidences.isEmpty()) {
            return HealthAnalysisResult.ClaimStatus.INSUFFICIENT;
        }
        if (requested != HealthAnalysisResult.ClaimStatus.SUPPORTED) {
            return requested;
        }
        boolean hasGuideline = evidences.stream().anyMatch(evidence ->
                evidence.studyType() == HealthAnalysisResult.EvidenceStudyType.GUIDELINE);
        return hasGuideline || evidences.size() >= 2
                ? HealthAnalysisResult.ClaimStatus.SUPPORTED
                : HealthAnalysisResult.ClaimStatus.NEEDS_REVIEW;
    }

    /** 고정 종합 상태 계산 */
    private HealthAnalysisResult.OverallStatus overallStatus(
            List<HealthAnalysisResult.Claim> claims
    ) {
        long contradicted = claims.stream()
                .filter(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.CONTRADICTED)
                .count();
        long supported = claims.stream()
                .filter(claim -> claim.status() == HealthAnalysisResult.ClaimStatus.SUPPORTED)
                .count();
        if (claims.get(0).status() == HealthAnalysisResult.ClaimStatus.CONTRADICTED
                || contradicted * 2 >= claims.size()) {
            return HealthAnalysisResult.OverallStatus.DOUBTFUL;
        }
        if (contradicted == 0 && supported * 3 >= claims.size() * 2L) {
            return HealthAnalysisResult.OverallStatus.RELIABLE;
        }
        return HealthAnalysisResult.OverallStatus.CAUTION;
    }

    /** 남은 전체 Deadline 확인 */
    private Duration remaining(Instant deadlineAt) {
        ensureBeforeDeadline(deadlineAt);
        return Duration.between(clock.instant(), deadlineAt);
    }

    /** Deadline 도달 차단 */
    private void ensureBeforeDeadline(Instant deadlineAt) {
        if (!clock.instant().isBefore(deadlineAt)) {
            throw new IllegalStateException("Health analysis deadline exceeded");
        }
    }

    /** JSON 직렬화 실패의 내부 분석 오류 변환 */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Gemini request serialization failed", exception);
        }
    }

    /** 주장 추출 응답 Schema */
    private Map<String, Object> claimExtractionSchema() {
        return objectSchema(
                Map.of("claims", arraySchema(
                        objectSchema(Map.of(
                                "order", Map.of("type", "integer", "minimum", 1, "maximum", 3),
                                "claim", Map.of("type", "string"),
                                "pubMedQuery", Map.of("type", "string")
                        ), List.of("order", "claim", "pubMedQuery")),
                        1,
                        3
                )),
                List.of("claims")
        );
    }

    /** 주장 판정 응답 Schema */
    private Map<String, Object> claimAssessmentSchema() {
        Map<String, Object> evidenceSchema = objectSchema(Map.of(
                "pmid", Map.of("type", "string"),
                "relationType", Map.of(
                        "type", "string",
                        "enum", List.of("SUPPORTS", "CONTRADICTS", "CONTEXT")
                ),
                "summary", Map.of("type", "string"),
                "conflictDescription", Map.of("type", List.of("string", "null"))
        ), List.of("pmid", "relationType", "summary", "conflictDescription"));
        Map<String, Object> decisionSchema = objectSchema(Map.of(
                "order", Map.of("type", "integer", "minimum", 1, "maximum", 3),
                "status", Map.of(
                        "type", "string",
                        "enum", List.of("SUPPORTED", "NEEDS_REVIEW", "CONTRADICTED", "INSUFFICIENT")
                ),
                "reason", Map.of("type", "string"),
                "evidences", arraySchema(evidenceSchema, 0, MAX_EVIDENCES_PER_CLAIM)
        ), List.of("order", "status", "reason", "evidences"));
        return objectSchema(
                Map.of("decisions", arraySchema(decisionSchema, 1, 3)),
                List.of("decisions")
        );
    }

    /** 닫힌 Object Schema 구성 */
    private Map<String, Object> objectSchema(
            Map<String, Object> properties,
            List<String> required
    ) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 제한 Array Schema 구성 */
    private Map<String, Object> arraySchema(Object items, int minimum, int maximum) {
        return Map.of(
                "type", "array",
                "items", items,
                "minItems", minimum,
                "maxItems", maximum
        );
    }

    /** 공백 정규화 */
    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    /** 문자 수 제한 */
    private static String truncate(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    /** 필수 문자열 제한 */
    private static String requireText(String value, int maxChars, String name) {
        if (value == null || value.isBlank() || value.length() > maxChars) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }

    /** 선택 문자열 제한 */
    private static String optionalText(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, maxChars, "Optional Gemini text");
    }

    /** Model ID 제한 */
    private static String requireModel(String value) {
        String model = requireText(value, 100, "Gemini model");
        if (!MODEL_PATTERN.matcher(model).matches()) {
            throw new IllegalArgumentException("Gemini model is invalid");
        }
        return model;
    }

    /** Gemini 주장 추출 응답 */
    private record ClaimExtraction(List<ClaimDraft> claims) {
    }

    /** 주장과 PubMed 검색어 */
    private record ClaimDraft(int order, String claim, String pubMedQuery) {
    }

    /** Gemini 주장 판정 응답 */
    private record ClaimAssessment(List<ClaimDecision> decisions) {
    }

    /** 주장별 상태와 사용 근거 */
    private record ClaimDecision(
            int order,
            String status,
            String reason,
            List<EvidenceDecision> evidences
    ) {
    }

    /** Gemini의 허용 근거 참조 */
    private record EvidenceDecision(
            String pmid,
            String relationType,
            String summary,
            String conflictDescription
    ) {
    }
}
