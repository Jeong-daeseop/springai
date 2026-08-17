package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.service.figma.FigmaApiQuery;
import com.krdevops.springai.service.figma.FigmaStylesResponse;
import com.krdevops.springai.service.figma.FigmaComponentsResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaApiClientTest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void fetchesNodeVersionWithTokenAndDepth() throws Exception {
        HttpServer server = server(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Figma-Token")).isEqualTo("test-token");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("ids=1%3A2", "depth=4");
            respond(exchange, 200, """
                    {"version":"version-1","nodes":{"1:2":{"document":{"type":"FRAME","name":"화면"}}}}
                    """);
        });

        var result = client(server, properties()).fetchNode(new FigmaReference("abcdef", "1:2"));

        assertThat(result.fileVersion()).isEqualTo("version-1");
        assertThat(result.document().path("type").asText()).isEqualTo("FRAME");
    }

    @Test
    void convertsAuthenticationAndInvalidResponseErrorsWithoutLeakingSecrets() throws Exception {
        HttpServer unauthorized = server(exchange -> respond(exchange, 401, "{}"));

        assertThatThrownBy(() -> client(unauthorized, properties())
                .fetchNode(new FigmaReference("secret-file-key", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("FIGMA_AUTH_FAILED");
                    assertThat(error.getMessage()).doesNotContain("test-token", "secret-file-key", "1:2");
                });

        HttpServer invalid = server(exchange -> respond(exchange, 200, "{\"nodes\":{}}"));
        assertThatThrownBy(() -> client(invalid, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RESPONSE_INVALID"));

        HttpServer badRequest = server(exchange -> respond(exchange, 400, "{}"));
        assertThatThrownBy(() -> client(badRequest, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_REQUEST_INVALID"));
    }

    @Test
    void convertsForbiddenAndNotFoundStatuses() throws Exception {
        HttpServer forbidden = server(exchange -> respond(exchange, 403, "{}"));
        assertThatThrownBy(() -> client(forbidden, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_ACCESS_DENIED"));

        HttpServer notFound = server(exchange -> respond(exchange, 404, "{}"));
        assertThatThrownBy(() -> client(notFound, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_REFERENCE_NOT_FOUND"));
    }

    /** 삭제된 노드는 404가 아니라 200 + nodes:{id:null}로 돌아오므로 별도 코드로 구분해야 한다. */
    @Test
    void reportsDeletedNodeAsNodeNotFoundInsteadOfInvalidResponse() throws Exception {
        HttpServer deletedNode = server(exchange ->
                respond(exchange, 200, "{\"version\":\"123\",\"nodes\":{\"1:2\":null}}"));

        assertThatThrownBy(() -> client(deletedNode, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_NODE_NOT_FOUND"));
    }

    /** 존재 확인은 노드 수만큼 왕복하지 않고 한 번의 GET으로 끝내며, 없는 노드만 돌려준다. */
    @Test
    void findMissingNodeIdsUsesOneRequestAndReportsOnlyAbsentNodes() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        List<String> queries = new java.util.ArrayList<>();
        HttpServer server = server(exchange -> {
            requests.incrementAndGet();
            queries.add(exchange.getRequestURI().getQuery());
            respond(exchange, 200, "{\"version\":\"123\",\"nodes\":{\"1:2\":{\"document\":{}},\"3:4\":null}}");
        });

        List<String> missing = client(server, properties())
                .findMissingNodeIds("abcdef", List.of("1:2", "3:4", "5:6"));

        assertThat(missing).containsExactly("3:4", "5:6");
        assertThat(requests.get()).isEqualTo(1);
        assertThat(queries.get(0)).contains("depth=1");
    }

    /** 파일 자체가 없으면(404) 요청한 노드 전부를 누락으로 본다. */
    @Test
    void findMissingNodeIdsTreatsMissingFileAsAllNodesMissing() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 404, "{}"));

        assertThat(client(server, properties()).findMissingNodeIds("abcdef", List.of("1:2", "3:4")))
                .containsExactly("1:2", "3:4");
    }

    /** 인증 실패는 "노드 없음"으로 오판하면 안 되므로 그대로 전파한다. */
    @Test
    void findMissingNodeIdsPropagatesAuthFailure() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 401, "{}"));

        assertThatThrownBy(() -> client(server, properties()).findMissingNodeIds("abcdef", List.of("1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_AUTH_FAILED"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        HttpServer malformed = server(exchange -> respond(exchange, 200, "{not-json"));

        assertThatThrownBy(() -> client(malformed, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RESPONSE_INVALID"));
    }

    @Test
    void retriesTransientFailureAndDoesNotFollowRedirect() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer transientServer = server(exchange -> {
            if (requests.incrementAndGet() == 1) respond(exchange, 500, "{}");
            else respond(exchange, 200, """
                    {"version":"v2","nodes":{"1:2":{"document":{"type":"FRAME"}}}}
                    """);
        });
        DesignVisionProperties properties = properties();
        var result = client(transientServer, properties)
                .fetchNode(new FigmaReference("abcdef", "1:2"));
        assertThat(result.fileVersion()).isEqualTo("v2");
        assertThat(requests).hasValue(2);

        HttpServer redirect = server(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + exchange.getLocalAddress().getPort()
                    + "/redirect-target");
            respond(exchange, 302, "");
        });
        assertThatThrownBy(() -> client(redirect, properties)
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_API_UNAVAILABLE"));
    }

    @Test
    void honorsRetryAfterCapAndMaximumAttempts() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "99");
            respond(exchange, 429, "{}");
        });
        DesignVisionProperties properties = properties();
        properties.getFigma().setRetryMaxDelaySeconds(1);

        assertThatThrownBy(() -> client(server, properties)
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RATE_LIMITED"));
        assertThat(requests).hasValue(1);
    }

    @Test
    void retriesRateLimitAndPersistentServerFailureOnlyToConfiguredMaximum() throws Exception {
        AtomicInteger rateLimitedRequests = new AtomicInteger();
        HttpServer rateLimited = server(exchange -> {
            rateLimitedRequests.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "0");
            respond(exchange, 429, "{}");
        });
        assertThatThrownBy(() -> client(rateLimited, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RATE_LIMITED"));
        assertThat(rateLimitedRequests).hasValue(3);

        AtomicInteger serverErrorRequests = new AtomicInteger();
        HttpServer unavailable = server(exchange -> {
            serverErrorRequests.incrementAndGet();
            respond(exchange, 503, "{}");
        });
        assertThatThrownBy(() -> client(unavailable, properties())
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_API_UNAVAILABLE"));
        assertThat(serverErrorRequests).hasValue(3);
    }

    @Test
    void convertsResponseTimeoutToUnavailableWithoutLeakingCauseDetails() throws Exception {
        HttpServer slow = server(exchange -> {
            try {
                Thread.sleep(1_500);
                respond(exchange, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        DesignVisionProperties properties = properties();
        properties.getFigma().setResponseTimeoutSeconds(1);
        properties.getFigma().setMaxAttempts(1);

        assertThatThrownBy(() -> client(slow, properties)
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("FIGMA_API_UNAVAILABLE");
                    assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
                });
    }

    @Test
    void rejectsResponseOverConfiguredByteLimit() throws Exception {
        String oversized = "x".repeat(1024 * 1024 + 1);
        HttpServer server = server(exchange -> respond(exchange, 200, oversized));
        DesignVisionProperties properties = properties();
        properties.getFigma().setMaxResponseMb(1);

        assertThatThrownBy(() -> client(server, properties)
                .fetchNode(new FigmaReference("abcdef", "1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RESPONSE_TOO_LARGE"));
    }

    @Test
    void queryStylesReturnsStyleMetadata() throws Exception {
        HttpServer server = server(exchange -> {
            assertThat(exchange.getRequestURI().getPath()).endsWith("/styles");
            respond(exchange, 200, """
                    {
                      "meta": {
                        "styles": [
                          {"key":"color1","file_key":"abcdef","node_id":"1:2","style_type":"FILL","name":"Primary","description":"Main color"}
                        ]
                      },
                      "error": false,
                      "status": 200
                    }
                    """);
        });

        var result = client(server, properties()).queryStyles("abcdef");

        assertThat(result.meta().styles()).hasSize(1);
        assertThat(result.meta().styles().get(0).name()).isEqualTo("Primary");
        assertThat(result.meta().styles().get(0).styleType()).isEqualTo("FILL");
    }

    @Test
    void queryComponentsReturnsComponentMetadata() throws Exception {
        HttpServer server = server(exchange -> {
            assertThat(exchange.getRequestURI().getPath()).endsWith("/components");
            respond(exchange, 200, """
                    {
                      "meta": {
                        "components": [
                          {
                            "key":"button1",
                            "file_key":"abcdef",
                            "node_id":"1:3",
                            "name":"Button",
                            "description":"Primary button",
                            "containing_frame":{"node_id":"1:1","name":"Components"}
                          }
                        ]
                      },
                      "error": false,
                      "status": 200
                    }
                    """);
        });

        var result = client(server, properties()).queryComponents("abcdef");

        assertThat(result.meta().components()).hasSize(1);
        assertThat(result.meta().components().get(0).name()).isEqualTo("Button");
        assertThat(result.meta().components().get(0).containingFrame().name()).isEqualTo("Components");
    }

    @Test
    void queryNodesPaginatedRequiresNodeId() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, "{}"));
        FigmaApiClient client = client(server, properties());
        FigmaApiQuery invalidQuery = new FigmaApiQuery("abcdef", null, 0, 50, false, 1);

        assertThatThrownBy(() -> client.queryNodesPaginated(invalidQuery))
                .isInstanceOfSatisfying(IllegalArgumentException.class,
                        error -> assertThat(error.getMessage()).contains("nodeId"));
    }

    /** R6-040: 여러 nodeId를 한 번의 GET(콤마 구분 다중 ID)으로 조회해 각각 별도 문서로 반환한다. */
    @Test
    void queryNodesPaginatedFetchesMultipleNodesInOneRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestURI().getRawQuery()).contains("ids=1%3A2,3%3A4");
            respond(exchange, 200, """
                    {"version":"v1","nodes":{
                      "1:2":{"document":{"type":"FRAME","name":"A"}},
                      "3:4":{"document":{"type":"FRAME","name":"B"}}
                    }}
                    """);
        });
        FigmaApiQuery query = FigmaApiQuery.paginated("abcdef", List.of("1:2", "3:4"), 0, 50);

        List<com.krdevops.springai.model.design.FigmaNodeDocument> result =
                client(server, properties()).queryNodesPaginated(query);

        assertThat(requests).hasValue(1);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).document().path("name").asText()).isEqualTo("A");
        assertThat(result.get(1).document().path("name").asText()).isEqualTo("B");
    }

    /** page/pageSize로 전체 nodeId 목록을 슬라이싱해 해당 구간만 조회한다. */
    @Test
    void queryNodesPaginatedSlicesRequestedPage() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestURI().getRawQuery()).contains("ids=3%3A4");
            respond(exchange, 200, """
                    {"version":"v1","nodes":{"3:4":{"document":{"type":"FRAME","name":"B"}}}}
                    """);
        });
        FigmaApiQuery secondPage = FigmaApiQuery.paginated(
                "abcdef", List.of("1:2", "3:4", "5:6"), 1, 1);

        List<com.krdevops.springai.model.design.FigmaNodeDocument> result =
                client(server, properties()).queryNodesPaginated(secondPage);

        assertThat(requests).hasValue(1);
        assertThat(result).singleElement().satisfies(
                doc -> assertThat(doc.document().path("name").asText()).isEqualTo("B"));
    }

    /** 요청 범위를 넘어서는 page는 HTTP 호출 없이 빈 결과를 반환한다. */
    @Test
    void queryNodesPaginatedReturnsEmptyPastLastPageWithoutHttpCall() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{}");
        });
        FigmaApiQuery beyondLastPage = FigmaApiQuery.paginated("abcdef", List.of("1:2"), 5, 1);

        List<com.krdevops.springai.model.design.FigmaNodeDocument> result =
                client(server, properties()).queryNodesPaginated(beyondLastPage);

        assertThat(result).isEmpty();
        assertThat(requests).hasValue(0);
    }

    /** 삭제된 노드(200 + null)는 예외 없이 결과에서 제외된다. */
    @Test
    void queryNodesPaginatedSkipsDeletedNodes() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, """
                {"version":"v1","nodes":{"1:2":{"document":{"type":"FRAME"}},"3:4":null}}
                """));
        FigmaApiQuery query = FigmaApiQuery.paginated("abcdef", List.of("1:2", "3:4"), 0, 50);

        List<com.krdevops.springai.model.design.FigmaNodeDocument> result =
                client(server, properties()).queryNodesPaginated(query);

        assertThat(result).hasSize(1);
    }

    /** 기존 callApi()의 응답 크기 제한·재시도가 그대로 재사용됨을 확인한다. */
    @Test
    void queryNodesPaginatedEnforcesResponseSizeLimitAndRetries() throws Exception {
        String oversized = "x".repeat(1024 * 1024 + 1);
        HttpServer oversizedServer = server(exchange -> respond(exchange, 200, oversized));
        DesignVisionProperties oversizedProps = properties();
        oversizedProps.getFigma().setMaxResponseMb(1);
        FigmaApiQuery query = FigmaApiQuery.paginated("abcdef", List.of("1:2"), 0, 50);

        assertThatThrownBy(() -> client(oversizedServer, oversizedProps).queryNodesPaginated(query))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RESPONSE_TOO_LARGE"));

        AtomicInteger requests = new AtomicInteger();
        HttpServer transientServer = server(exchange -> {
            if (requests.incrementAndGet() == 1) respond(exchange, 500, "{}");
            else respond(exchange, 200, """
                    {"version":"v2","nodes":{"1:2":{"document":{"type":"FRAME"}}}}
                    """);
        });

        List<com.krdevops.springai.model.design.FigmaNodeDocument> result =
                client(transientServer, properties()).queryNodesPaginated(query);

        assertThat(requests).hasValue(2);
        assertThat(result).hasSize(1);
    }

    @Test
    void queryStylesHandlesApiErrors() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 401, "{}"));

        assertThatThrownBy(() -> client(server, properties()).queryStyles("abcdef"))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_AUTH_FAILED"));
    }

    @Test
    void queryComponentsHandlesApiErrors() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 404, "{}"));

        assertThatThrownBy(() -> client(server, properties()).queryComponents("abcdef"))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_REFERENCE_NOT_FOUND"));
    }

    // ===== R6-T10: 이미지 URL 조회 =====

    @Test
    void queryImagesReturnsUrlsForRequestedNodes() throws Exception {
        HttpServer server = imagesServer(exchange -> {
            assertThat(exchange.getRequestURI().getPath()).endsWith("/images/abcdef");
            assertThat(exchange.getRequestURI().getRawQuery()).contains("ids=1%3A2,3%3A4");
            respond(exchange, 200, """
                    {"err":null,"images":{"1:2":"https://figma-images.example/1-2.png","3:4":"https://figma-images.example/3-4.png"}}
                    """);
        });

        var result = client(server, properties()).queryImages("abcdef", List.of("1:2", "3:4"));

        assertThat(result.imageUrlsByNodeId()).containsEntry("1:2", "https://figma-images.example/1-2.png");
        assertThat(result.failedNodeIds()).isEmpty();
    }

    /** 개별 노드의 렌더 실패는 전체 실패가 아니라 images 맵의 null 값으로 온다. */
    @Test
    void queryImagesReportsPerNodeRenderFailureWithoutFailingTheWholeCall() throws Exception {
        HttpServer server = imagesServer(exchange -> respond(exchange, 200, """
                {"err":null,"images":{"1:2":"https://figma-images.example/1-2.png","3:4":null}}
                """));

        var result = client(server, properties()).queryImages("abcdef", List.of("1:2", "3:4"));

        assertThat(result.imageUrlsByNodeId()).containsEntry("1:2", "https://figma-images.example/1-2.png");
        assertThat(result.failedNodeIds()).containsExactly("3:4");
    }

    @Test
    void queryImagesThrowsOnTopLevelError() throws Exception {
        HttpServer server = imagesServer(exchange -> respond(exchange, 200, """
                {"err":"Invalid node ids","images":{}}
                """));

        assertThatThrownBy(() -> client(server, properties()).queryImages("abcdef", List.of("1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_IMAGE_EXPORT_FAILED"));
    }

    @Test
    void queryImagesHandlesPermissionAndRateLimitErrors() throws Exception {
        HttpServer forbidden = imagesServer(exchange -> respond(exchange, 403, "{}"));
        assertThatThrownBy(() -> client(forbidden, properties()).queryImages("abcdef", List.of("1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_ACCESS_DENIED"));

        AtomicInteger requests = new AtomicInteger();
        HttpServer rateLimited = imagesServer(exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "0");
            respond(exchange, 429, "{}");
        });
        assertThatThrownBy(() -> client(rateLimited, properties()).queryImages("abcdef", List.of("1:2")))
                .isInstanceOfSatisfying(FigmaApiException.class,
                        error -> assertThat(error.code()).isEqualTo("FIGMA_RATE_LIMITED"));
        assertThat(requests).hasValue(3);
    }

    @Test
    void queryImagesRejectsEmptyNodeIdList() throws Exception {
        HttpServer server = imagesServer(exchange -> respond(exchange, 200, "{}"));

        assertThatThrownBy(() -> client(server, properties()).queryImages("abcdef", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Figma는 발급한 이미지 URL의 만료 시각을 알려주지 않으므로, 조회 시각 기준 TTL로 판정한다. */
    @Test
    void figmaImageUrlsExpiresAfterDefaultTtl() {
        var result = new FigmaApiClient.FigmaImageUrls(
                Map.of("1:2", "https://figma-images.example/1-2.png"), List.of(),
                java.time.Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(result.isExpired(java.time.Instant.parse("2026-01-01T00:20:00Z"))).isFalse();
        assertThat(result.isExpired(java.time.Instant.parse("2026-01-01T00:31:00Z"))).isTrue();
    }

    private DesignVisionProperties properties() {
        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setEnabled(true);
        properties.getFigma().setAccessToken("test-token");
        return properties;
    }

    private FigmaApiClient client(HttpServer server, DesignVisionProperties properties) {
        return new FigmaApiClient(properties, new ObjectMapper(), HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build(), millis -> { },
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/files", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private HttpServer imagesServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        servers.add(server);
        return server;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
