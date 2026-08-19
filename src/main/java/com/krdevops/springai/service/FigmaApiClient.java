package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.config.OperationalResilienceProperties;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.service.figma.FigmaApiQuery;
import com.krdevops.springai.service.figma.FigmaComponentsResponse;
import com.krdevops.springai.service.figma.FigmaImagesResponse;
import com.krdevops.springai.service.figma.FigmaStylesResponse;
import com.krdevops.springai.service.figma.FigmaTeamComponentsResponse;
import com.krdevops.springai.service.figma.FigmaTeamStylesResponse;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class FigmaApiClient {

    private static final String BASE_URL = "https://api.figma.com/v1";

    private final DesignVisionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Sleeper sleeper;
    private final String baseUrl;
    private final ExternalCallGuard guard;

    @Autowired
    public FigmaApiClient(DesignVisionProperties properties, ObjectMapper objectMapper,
                          ExternalCallGuard guard) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1,
                        properties.getFigma().getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Thread::sleep, BASE_URL, guard);
    }

    FigmaApiClient(DesignVisionProperties properties, ObjectMapper objectMapper,
                   HttpClient httpClient, Sleeper sleeper, String baseUrl) {
        this(properties, objectMapper, httpClient, sleeper, baseUrl,
                new ExternalCallGuard(new OperationalResilienceProperties()));
    }

    private FigmaApiClient(DesignVisionProperties properties, ObjectMapper objectMapper,
                           HttpClient httpClient, Sleeper sleeper, String baseUrl,
                           ExternalCallGuard guard) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.sleeper = sleeper;
        this.baseUrl = baseUrl;
        this.guard = guard;
    }

    public FigmaNodeDocument fetchNode(FigmaReference reference) {
        ensureEnabled();
        return guard.execute(ExternalDependency.FIGMA, () -> fetchNodeOnce(reference));
    }

    /**
     * R6-T08: 노드 응답의 parent ancestry를 따라 요청한 Page 소속을 검증한다.
     * Figma NODES 응답은 페이지 조상 정보를 parent 객체로 제공하므로 별도 파일 전체 다운로드 없이
     * fail-closed로 검사한다. ancestry가 생략된 응답은 일치로 추정하지 않고 오류로 거부한다.
     */
    public void validateNodeBelongsToPage(FigmaReference reference, String expectedPageId) {
        if (expectedPageId == null || expectedPageId.isBlank()) {
            throw new IllegalArgumentException("expectedPageId는 필수입니다.");
        }
        FigmaNodeDocument node = fetchNode(reference);
        JsonNode current = node.document();
        java.util.Set<String> visited = new java.util.HashSet<>();
        boolean ancestrySeen = false;
        while (current != null && current.isObject()) {
            String id = current.path("id").asText(null);
            String type = current.path("type").asText(null);
            if (expectedPageId.equals(id) && "PAGE".equals(type)) {
                return;
            }
            JsonNode parent = current.path("parent");
            if (!parent.isObject() || id != null && !visited.add(id)) {
                break;
            }
            ancestrySeen = true;
            current = parent;
        }
        String code = ancestrySeen ? "FIGMA_PAGE_MISMATCH" : "FIGMA_PAGE_ANCESTRY_UNAVAILABLE";
        throw new FigmaApiException(code, 422,
                "Figma 노드가 요청한 Page에 속하지 않거나 Page ancestry가 응답에 없습니다.");
    }

    /** Figma 연동이 켜져 있고 토큰이 있는지. 호출 전에 fail-closed 여부를 판단할 때 쓴다. */
    public boolean isFigmaEnabled() {
        return properties.getFigma().isEnabled() && !properties.getFigma().getAccessToken().isBlank();
    }

    /**
     * 여러 노드의 존재 여부를 한 번의 GET으로 확인하고, 현재 파일에 없는 nodeId만 돌려준다.
     * Figma NODES 엔드포인트는 콤마 구분 다중 ID를 지원하므로 노드 수만큼 왕복하지 않는다
     * (노드별 순차 호출은 rate limit 한 번에 전체가 막힌다).
     *
     * <p>존재 확인만 하므로 {@code depth=1}로 문서 본문을 최소화한다. 파일 자체가 없으면(404)
     * 요청한 전부를 누락으로 본다. 인증 실패·rate limit 등 다른 오류는 "노드 없음"으로 오판하면
     * 안 되므로 그대로 전파한다.
     */
    public List<String> findMissingNodeIds(String fileKey, List<String> nodeIds) {
        ensureEnabled();
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        return guard.execute(ExternalDependency.FIGMA, () -> findMissingNodeIdsOnce(fileKey, nodeIds));
    }

    private List<String> findMissingNodeIdsOnce(String fileKey, List<String> nodeIds) {
        String ids = nodeIds.stream().map(this::encodeQuery).collect(java.util.stream.Collectors.joining(","));
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/nodes?ids=" + ids + "&depth=1");
        JsonNode root;
        try {
            root = callApi(uri, JsonNode.class);
        } catch (FigmaApiException e) {
            if ("FIGMA_REFERENCE_NOT_FOUND".equals(e.code())) {
                return List.copyOf(nodeIds);
            }
            throw e;
        }
        if (root.path("version").asText(null) == null) {
            throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                    "Figma API 응답에 파일 버전이 없습니다.");
        }
        JsonNode nodes = root.path("nodes");
        return nodeIds.stream()
                .filter(nodeId -> {
                    JsonNode node = nodes.path(nodeId);
                    return node.isMissingNode() || node.isNull();
                })
                .toList();
    }

    private FigmaNodeDocument fetchNodeOnce(FigmaReference reference) {
        URI uri = URI.create(baseUrl + "/files/" + encodePath(reference.fileKey())
                + "/nodes?ids=" + encodeQuery(reference.nodeId())
                + "&depth=" + effectiveDepth());
        int maxAttempts = Math.max(1, Math.min(5, properties.getFigma().getMaxAttempts()));
        Throwable last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(Math.max(1,
                                properties.getFigma().getResponseTimeoutSeconds())))
                        .header("Accept", "application/json")
                        .header("X-Figma-Token", properties.getFigma().getAccessToken())
                        .GET().build();
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;
                try (InputStream input = response.body()) {
                    body = readLimited(input, maxResponseBytes());
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parse(reference, body);
                }
                if (retryable(response.statusCode()) && attempt < maxAttempts) {
                    sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                throw statusException(response.statusCode());
            } catch (FigmaApiException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FigmaApiException("FIGMA_API_UNAVAILABLE", 0,
                        "Figma API 호출이 중단되었습니다.", e);
            } catch (Exception e) {
                last = e;
                if (attempt < maxAttempts) {
                    sleep(backoffMillis(attempt));
                    continue;
                }
            }
        }
        throw new FigmaApiException("FIGMA_API_UNAVAILABLE", 0,
                "Figma API를 호출할 수 없습니다.", last);
    }

    private FigmaNodeDocument parse(FigmaReference reference, byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String version = root.path("version").asText(null);
            if (version == null || version.isBlank()) {
                throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                        "Figma API 응답에 파일 버전이 없습니다.");
            }
            // 삭제된 노드를 조회하면 Figma는 404가 아니라 200 + nodes:{id:null}을 돌려준다.
            JsonNode node = root.path("nodes").path(reference.nodeId());
            if (node.isMissingNode() || node.isNull()) {
                throw new FigmaApiException("FIGMA_NODE_NOT_FOUND", 200,
                        "Figma 파일에 해당 노드가 없습니다.");
            }
            JsonNode document = node.path("document");
            if (!document.isObject()) {
                throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                        "Figma API 응답에 노드 문서가 없습니다.");
            }
            return new FigmaNodeDocument(version, document.deepCopy());
        } catch (FigmaApiException e) {
            throw e;
        } catch (Exception e) {
            throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                    "Figma API 응답을 해석할 수 없습니다.", e);
        }
    }

    private void ensureEnabled() {
        if (!properties.getFigma().isEnabled()) {
            throw new IllegalStateException("Figma 디자인 분석이 비활성화되어 있습니다.");
        }
        if (properties.getFigma().getAccessToken().isBlank()) {
            throw new IllegalStateException("FIGMA_ACCESS_TOKEN 설정이 필요합니다.");
        }
    }

    private byte[] readLimited(InputStream input, long maxBytes) throws Exception {
        byte[] bytes = input.readNBytes((int) Math.min(Integer.MAX_VALUE, maxBytes + 1));
        if (bytes.length > maxBytes) {
            throw new FigmaApiException("FIGMA_RESPONSE_TOO_LARGE", 200,
                    "Figma API 응답 크기 제한을 초과했습니다.");
        }
        return bytes;
    }

    private boolean retryable(int status) { return status == 429 || status >= 500; }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            try {
                long requested = Long.parseLong(retryAfter.get()) * 1000L;
                long cap = Math.max(1, properties.getFigma().getRetryMaxDelaySeconds()) * 1000L;
                if (requested > cap) {
                    throw new FigmaApiException("FIGMA_RATE_LIMITED", 429,
                            "Figma API 재시도 대기 시간이 허용 상한을 초과했습니다.");
                }
                return Math.max(0, requested);
            } catch (NumberFormatException ignored) {
                // HTTP-date 형식은 짧은 기본 백오프로 대체한다.
            }
        }
        return backoffMillis(attempt);
    }

    private long backoffMillis(int attempt) {
        long cap = Math.max(1, properties.getFigma().getRetryMaxDelaySeconds()) * 1000L;
        long base = Math.min(cap, 250L * (1L << Math.min(attempt - 1, 5)));
        return Math.min(cap, base + ThreadLocalRandom.current().nextLong(100));
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FigmaApiException("FIGMA_API_UNAVAILABLE", 0,
                    "Figma API 재시도가 중단되었습니다.", e);
        }
    }

    private FigmaApiException statusException(int status) {
        return switch (status) {
            case 400 -> new FigmaApiException("FIGMA_REQUEST_INVALID", status, "Figma API 요청이 유효하지 않습니다.");
            case 401 -> new FigmaApiException("FIGMA_AUTH_FAILED", status, "Figma 인증에 실패했습니다.");
            case 403 -> new FigmaApiException("FIGMA_ACCESS_DENIED", status, "Figma 파일 접근 권한이 없습니다.");
            case 404 -> new FigmaApiException("FIGMA_REFERENCE_NOT_FOUND", status, "Figma 파일 또는 노드를 찾을 수 없습니다.");
            case 429 -> new FigmaApiException("FIGMA_RATE_LIMITED", status, "Figma API 호출 한도를 초과했습니다.");
            default -> new FigmaApiException("FIGMA_API_UNAVAILABLE", status, "Figma API 호출에 실패했습니다.");
        };
    }

    private int effectiveDepth() { return Math.max(1, Math.min(10, properties.getFigma().getDepthLimit())); }
    private long maxResponseBytes() { return Math.max(1, properties.getFigma().getMaxResponseMb()) * 1024L * 1024L; }
    private String encodePath(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private String encodeQuery(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    /**
     * R6-040: Pagination을 지원하는 다중 노드 조회.
     * Figma NODES 엔드포인트(GET /v1/files/{fileKey}/nodes?ids=...)는 콤마 구분 다중 ID를
     * 한 번에 조회할 수 있지만 offset/limit 개념은 없다. 이 메서드는 {@code query.resolvedNodeIds()}
     * (nodeId 단건 또는 nodeIds 다건)를 {@code pageSize} 단위로 슬라이싱해 {@code page}번째 구간만
     * 한 번의 GET으로 조회한다 — 대량 nodeId를 URL 길이 제한 없이 나눠 처리하기 위함이다.
     * 삭제된 노드(200 + nodes:{id:null})는 조용히 건너뛴다({@link #findMissingNodeIds}로 별도 확인 가능).
     */
    public List<FigmaNodeDocument> queryNodesPaginated(FigmaApiQuery query) {
        ensureEnabled();
        if (query == null) {
            throw new IllegalArgumentException("query는 필수입니다");
        }
        List<String> allIds = query.resolvedNodeIds();
        if (allIds.isEmpty()) {
            throw new IllegalArgumentException("nodeId 또는 nodeIds 중 하나는 필수입니다");
        }
        int fromIndex = query.page() * query.pageSize();
        if (fromIndex >= allIds.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + query.pageSize(), allIds.size());
        List<String> pageIds = allIds.subList(fromIndex, toIndex);
        return guard.execute(ExternalDependency.FIGMA,
                () -> queryNodesPaginatedOnce(query.fileKey(), pageIds, query.depth()));
    }

    private List<FigmaNodeDocument> queryNodesPaginatedOnce(String fileKey, List<String> nodeIds, int depth) {
        String ids = nodeIds.stream().map(this::encodeQuery).collect(Collectors.joining(","));
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/nodes?ids=" + ids
                + "&depth=" + Math.max(1, Math.min(10, depth)));
        JsonNode root = callApi(uri, JsonNode.class);
        String version = root.path("version").asText(null);
        if (version == null || version.isBlank()) {
            throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                    "Figma API 응답에 파일 버전이 없습니다.");
        }
        JsonNode nodes = root.path("nodes");
        List<FigmaNodeDocument> result = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            JsonNode entry = nodes.path(nodeId);
            if (entry.isMissingNode() || entry.isNull()) {
                continue;
            }
            JsonNode document = entry.path("document");
            if (!document.isObject()) {
                continue;
            }
            result.add(new FigmaNodeDocument(version, document.deepCopy()));
        }
        return List.copyOf(result);
    }

    /**
     * Figma 파일의 모든 Styles 조회.
     * GET /v1/files/{fileKey}/styles
     */
    public FigmaStylesResponse queryStyles(String fileKey) {
        ensureEnabled();
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/styles");
        return guard.execute(ExternalDependency.FIGMA,
                () -> callApi(uri, FigmaStylesResponse.class));
    }

    /**
     * Figma 파일의 모든 Components 조회.
     * GET /v1/files/{fileKey}/components
     */
    public FigmaComponentsResponse queryComponents(String fileKey) {
        ensureEnabled();
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/components");
        return guard.execute(ExternalDependency.FIGMA,
                () -> callApi(uri, FigmaComponentsResponse.class));
    }

    /**
     * R6-T10: Team 전체 Published Components 단일 페이지 조회. 파일 단위 {@link #queryComponents}와
     * 달리 결과가 방대할 수 있어 Figma가 실제로 제공하는 {@code page_size}/{@code after} cursor
     * pagination을 그대로 노출한다. GET /v1/teams/{teamId}/components?page_size=...&after=...
     */
    public FigmaTeamComponentsResponse queryTeamComponents(String teamId, Long afterCursor, int pageSize) {
        ensureEnabled();
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 필수입니다");
        }
        URI uri = teamPageUri("components", teamId, afterCursor, pageSize);
        return guard.execute(ExternalDependency.FIGMA,
                () -> callApi(uri, FigmaTeamComponentsResponse.class));
    }

    /**
     * R6-T10: cursor가 남아 있는 동안 반복 호출해 Team 전체 Components를 모은다. 응답의
     * {@code cursor.after}가 방금 요청에 쓴 cursor와 같으면(서버가 진행 없음을 알려온 것) 그
     * 페이지는 버리고 즉시 멈춘다 — 같은 cursor로 또 요청하면 중복·무한 루프가 되기 때문이다.
     * {@code maxPages}는 그런 신호조차 없는 비정상 응답에 대한 최후 방어선이다.
     */
    public List<FigmaTeamComponentsResponse.ComponentRef> queryAllTeamComponents(
            String teamId, int pageSize, int maxPages) {
        List<FigmaTeamComponentsResponse.ComponentRef> all = new ArrayList<>();
        Long cursor = null;
        for (int page = 0; page < Math.max(1, maxPages); page++) {
            FigmaTeamComponentsResponse response = queryTeamComponents(teamId, cursor, pageSize);
            Long next = response.meta().cursor() == null ? null : response.meta().cursor().after();
            if (next != null && next.equals(cursor)) {
                break;
            }
            all.addAll(response.meta().components());
            if (next == null) {
                break;
            }
            cursor = next;
        }
        return List.copyOf(all);
    }

    /**
     * R6-T10: Team 전체 Published Styles 단일 페이지 조회. GET /v1/teams/{teamId}/styles?page_size=...&after=...
     */
    public FigmaTeamStylesResponse queryTeamStyles(String teamId, Long afterCursor, int pageSize) {
        ensureEnabled();
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId는 필수입니다");
        }
        URI uri = teamPageUri("styles", teamId, afterCursor, pageSize);
        return guard.execute(ExternalDependency.FIGMA,
                () -> callApi(uri, FigmaTeamStylesResponse.class));
    }

    /** R6-T10: {@link #queryAllTeamComponents}와 동일한 반복 조회·중단 규칙을 Styles에 적용한다. */
    public List<FigmaTeamStylesResponse.StyleRef> queryAllTeamStyles(String teamId, int pageSize, int maxPages) {
        List<FigmaTeamStylesResponse.StyleRef> all = new ArrayList<>();
        Long cursor = null;
        for (int page = 0; page < Math.max(1, maxPages); page++) {
            FigmaTeamStylesResponse response = queryTeamStyles(teamId, cursor, pageSize);
            Long next = response.meta().cursor() == null ? null : response.meta().cursor().after();
            if (next != null && next.equals(cursor)) {
                break;
            }
            all.addAll(response.meta().styles());
            if (next == null) {
                break;
            }
            cursor = next;
        }
        return List.copyOf(all);
    }

    private URI teamPageUri(String resource, String teamId, Long afterCursor, int pageSize) {
        StringBuilder builder = new StringBuilder(baseUrl)
                .append("/teams/").append(encodePath(teamId)).append('/').append(resource)
                .append("?page_size=").append(Math.max(1, Math.min(pageSize, 1000)));
        if (afterCursor != null) {
            builder.append("&after=").append(afterCursor);
        }
        return URI.create(builder.toString());
    }

    /**
     * R6-T10: 지정 노드의 렌더 이미지 URL 조회. GET /v1/images/{fileKey}?ids=...
     * Figma는 발급한 이미지 URL의 만료 시각을 응답에 포함하지 않으므로(공식 문서상 약 30분),
     * 이 클라이언트가 조회 시각을 기록해 {@link FigmaImageUrls#isExpired} 판정 기준으로 쓴다.
     * 개별 노드의 렌더 실패는 {@code images} 맵에서 값이 null로 오며 전체 실패로 취급하지 않는다.
     */
    public FigmaImageUrls queryImages(String fileKey, List<String> nodeIds) {
        ensureEnabled();
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("nodeIds는 필수입니다");
        }
        String ids = nodeIds.stream().map(this::encodeQuery).collect(Collectors.joining(","));
        URI uri = URI.create(baseUrl + "/images/" + encodePath(fileKey) + "?ids=" + ids);
        FigmaImagesResponse response = guard.execute(ExternalDependency.FIGMA,
                () -> callApi(uri, FigmaImagesResponse.class));
        if (response.err() != null) {
            throw new FigmaApiException("FIGMA_IMAGE_EXPORT_FAILED", 502, response.err());
        }
        Map<String, String> images = response.images() == null ? Map.of() : response.images();
        List<String> failedNodeIds = nodeIds.stream()
                .filter(id -> images.get(id) == null)
                .toList();
        return new FigmaImageUrls(images, failedNodeIds, Instant.now());
    }

    /** R6-T10: 조회된 이미지 URL과 조회 시각. TTL은 Figma가 문서화한 약 30분을 기본값으로 쓴다. */
    public record FigmaImageUrls(
            Map<String, String> imageUrlsByNodeId, List<String> failedNodeIds, Instant fetchedAt) {
        private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

        public boolean isExpired(Instant now) {
            return now.isAfter(fetchedAt.plus(DEFAULT_TTL));
        }
    }

    /**
     * 제네릭 API 호출 (재시도 포함).
     */
    private <T> T callApi(URI uri, Class<T> responseType) {
        int maxAttempts = Math.max(1, Math.min(5, properties.getFigma().getMaxAttempts()));
        Throwable last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(Math.max(1,
                                properties.getFigma().getResponseTimeoutSeconds())))
                        .header("Accept", "application/json")
                        .header("X-Figma-Token", properties.getFigma().getAccessToken())
                        .GET().build();
                HttpResponse<InputStream> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;
                try (InputStream input = response.body()) {
                    body = readLimited(input, maxResponseBytes());
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(body, responseType);
                }
                if (retryable(response.statusCode()) && attempt < maxAttempts) {
                    sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                throw statusException(response.statusCode());
            } catch (FigmaApiException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FigmaApiException("FIGMA_API_UNAVAILABLE", 0,
                        "Figma API 호출이 중단되었습니다.", e);
            } catch (Exception e) {
                last = e;
                if (attempt < maxAttempts) {
                    sleep(backoffMillis(attempt));
                    continue;
                }
            }
        }
        throw new FigmaApiException("FIGMA_API_UNAVAILABLE", 0,
                "Figma API를 호출할 수 없습니다.", last);
    }

    /**
     * JSON을 지정된 타입으로 파싱.
     */
    private <T> T parseResponse(byte[] body, Class<T> responseType) {
        try {
            return objectMapper.readValue(body, responseType);
        } catch (Exception e) {
            throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                    "Figma API 응답을 해석할 수 없습니다.", e);
        }
    }

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
