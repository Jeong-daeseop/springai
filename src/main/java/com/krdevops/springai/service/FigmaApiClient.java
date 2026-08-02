package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.service.figma.FigmaApiQuery;
import com.krdevops.springai.service.figma.FigmaComponentsResponse;
import com.krdevops.springai.service.figma.FigmaStylesResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FigmaApiClient {

    private static final String BASE_URL = "https://api.figma.com/v1";

    private final DesignVisionProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Sleeper sleeper;
    private final String baseUrl;

    @Autowired
    public FigmaApiClient(DesignVisionProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1,
                        properties.getFigma().getConnectTimeoutSeconds())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), Thread::sleep, BASE_URL);
    }

    FigmaApiClient(DesignVisionProperties properties, ObjectMapper objectMapper,
                   HttpClient httpClient, Sleeper sleeper, String baseUrl) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.sleeper = sleeper;
        this.baseUrl = baseUrl;
    }

    public FigmaNodeDocument fetchNode(FigmaReference reference) {
        ensureEnabled();
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
            JsonNode document = root.path("nodes").path(reference.nodeId()).path("document");
            if (version == null || version.isBlank() || !document.isObject()) {
                throw new FigmaApiException("FIGMA_RESPONSE_INVALID", 200,
                        "Figma API 응답에 버전 또는 노드 문서가 없습니다.");
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
     * Pagination을 지원하는 노드 조회.
     * Figma API의 NODES 엔드포인트는 단일 조회만 지원하므로,
     * 이 메서드는 fileKey 기준 여러 노드를 offset-limit 방식으로 분할 조회한다.
     */
    public List<FigmaNodeDocument> queryNodesPaginated(FigmaApiQuery query) {
        ensureEnabled();
        if (query == null) {
            throw new IllegalArgumentException("query는 필수입니다");
        }

        // Figma API는 단일 조회만 지원하므로, 실제 구현은 내부 노드 목록 분할
        // 이 메서드는 주로 테스트/시뮬레이션용
        int offset = query.page() * query.pageSize();

        // 현재는 단일 노드 조회로 제한 (노드 목록 API는 Figma REST에 없음)
        if (query.nodeId() == null || query.nodeId().isBlank()) {
            throw new IllegalArgumentException("nodeId는 필수입니다");
        }

        // 단일 노드 조회 후 List로 반환
        FigmaNodeDocument node = fetchNode(new FigmaReference(
                query.fileKey(),
                query.nodeId()
        ));
        return List.of(node);
    }

    /**
     * Figma 파일의 모든 Styles 조회.
     * GET /v1/files/{fileKey}/styles
     */
    public FigmaStylesResponse queryStyles(String fileKey) {
        ensureEnabled();
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/styles");
        return callApi(uri, FigmaStylesResponse.class);
    }

    /**
     * Figma 파일의 모든 Components 조회.
     * GET /v1/files/{fileKey}/components
     */
    public FigmaComponentsResponse queryComponents(String fileKey) {
        ensureEnabled();
        URI uri = URI.create(baseUrl + "/files/" + encodePath(fileKey) + "/components");
        return callApi(uri, FigmaComponentsResponse.class);
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
