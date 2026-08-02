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
