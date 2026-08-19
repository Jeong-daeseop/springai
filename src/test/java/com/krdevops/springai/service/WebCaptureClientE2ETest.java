package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.OperationalResilienceProperties;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.mapper.DesignAnalysisRepository;
import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureProfile;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedDesignPackageManifest;
import com.krdevops.springai.model.capture.RenderedNode;
import com.krdevops.springai.model.capture.ViewportSpec;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.policy.WebCaptureProjectionPolicy;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * R7-T01~T03: {@link WebCaptureRelease1FlowTest}는 {@link WebCaptureClient}를 완전히 mock으로
 * 대체해서 실제 HTTP 호출 경로(요청 직렬화·응답 파싱·오류 상태 코드 처리)는 한 번도 실행하지
 * 않았다. 이 테스트는 extractor 마이크로서비스를 흉내내는 로컬 stub HTTP 서버를 세워
 * {@link WebCaptureClient}가 실제로 요청을 보내고 응답을 처리하는 경로까지 검증한다.
 */
class WebCaptureClientE2ETest {

    private final List<HttpServer> servers = new ArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    /** R7-T01: 공개 URL 캡처 → 후보 Spec → FigmaScreenSpec 흐름의 앞단(캡처→분석)을 실제 HTTP 경로로 검증한다. */
    @Test
    void t01_publicUrlCaptureProducesArtifactAndAnalysisThroughRealHttpCall(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer extractor = extractorServer(exchange -> {
            requestCount.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("X-Extractor-Key")).isEqualTo("extractor-secret");
            var requestIds = readCaptureIds(mapper, exchange);
            byte[] figpack = packUnchecked(mapper, requestIds[0], requestIds[1], "목록", List.of(
                    field("title", "textbox", "제목", null),
                    field("save", "button", null, "저장")));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        CaptureArtifactSummary captured = orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud"));

        assertThat(captured.artifactId()).isNotBlank();
        assertThat(requestCount).hasValue(1);

        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        when(repository.findExact(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.saveOrGet(any())).thenAnswer(invocation ->
                new DesignAnalysisSaveOutcome(invocation.getArgument(0), true));
        WebCaptureAnalysisService analysisService = new WebCaptureAnalysisService(
                artifactService(properties, mapper), new WebCaptureProjectionPolicy(), new RenderedDesignSpecMapper(),
                new WebCaptureCacheKeyFactory(), repository, properties);

        var analysis = analysisService.analyze(captured.artifactId(), "crud");

        assertThat(analysis.sourceMetadata().sourceType()).isEqualTo(DesignSourceType.WEB_CAPTURE);
        // "저장" 라벨은 ActionSpec으로 의미 분류되어 원문 대신 type=SAVE로 남는다(원문 보존이 아니라
        // 의도된 변환) — fieldHint는 원문 라벨을, action은 분류된 type을 각각 검증한다.
        assertThat(analysis.uiSpec().toString()).contains("제목").contains("type=SAVE");
    }

    /** R7-T02: 로그인 화면(아이디/비밀번호 입력) 캡처 fixture가 실제 HTTP 경로로도 PII 마스킹을 지키며 변환된다. */
    @Test
    void t02_loginScreenCaptureFixtureIsConvertedWithoutLeakingCredentials(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HttpServer extractor = extractorServer(exchange -> {
            var requestIds = readCaptureIds(mapper, exchange);
            byte[] figpack = packUnchecked(mapper, requestIds[0], requestIds[1], "로그인", List.of(
                    field("userId", "textbox", "아이디", null),
                    field("password", "textbox", "PASSWORD_HASH", null),
                    field("login", "button", null, "로그인")));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        CaptureArtifactSummary captured = orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/login.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "login"));

        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        when(repository.findExact(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.saveOrGet(any())).thenAnswer(invocation ->
                new DesignAnalysisSaveOutcome(invocation.getArgument(0), true));
        WebCaptureAnalysisService analysisService = new WebCaptureAnalysisService(
                artifactService(properties, mapper), new WebCaptureProjectionPolicy(), new RenderedDesignSpecMapper(),
                new WebCaptureCacheKeyFactory(), repository, properties);

        var analysis = analysisService.analyze(captured.artifactId(), "login");

        assertThat(analysis.uiSpec().toString()).contains("아이디").contains("ActionSpec");
        assertThat(analysis.uiSpec().toString()).doesNotContain("PASSWORD_HASH");
    }

    /** R7-T02: MCP가 받은 불투명 storageStateRef를 extractor 요청에 그대로 전달한다. */
    @Test
    void t02_storageStateRefIsForwardedWithoutAcceptingCredentials(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<String> forwardedStorageStateRef = new AtomicReference<>();
        HttpServer extractor = extractorServer(exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            forwardedStorageStateRef.set(body.path("storageStateRef").asText(null));
            var requestIds = new String[]{body.get("captureId").asText(), body.get("documentKey").asText()};
            byte[] figpack = packUnchecked(mapper, requestIds[0], requestIds[1], "인증 화면", List.of(
                    field("title", "textbox", "제목", null)));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);
        String storageStateRef = "11111111-1111-4111-8111-111111111111";

        CaptureArtifactSummary captured = orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/qna/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null,
                "crud", storageStateRef));

        assertThat(captured.artifactId()).isNotBlank();
        assertThat(forwardedStorageStateRef).hasValue(storageStateRef);
    }

    /** R7(04번 문서 §10): interactions가 extractor 요청 본문에 그대로 전달된다. */
    @Test
    void t07_interactionsAreForwardedToExtractor(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<com.fasterxml.jackson.databind.JsonNode> forwardedInteractions = new AtomicReference<>();
        HttpServer extractor = extractorServer(exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            forwardedInteractions.set(body.path("interactions"));
            var requestIds = new String[]{body.get("captureId").asText(), body.get("documentKey").asText()};
            byte[] figpack = packUnchecked(mapper, requestIds[0], requestIds[1], "SPA", List.of(
                    field("title", "textbox", "제목", null)));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);
        var interactions = List.of(
                new com.krdevops.springai.model.capture.InteractionStep("click", "#reveal", null),
                new com.krdevops.springai.model.capture.InteractionStep("fill", "#name", "홍길동"));

        CaptureArtifactSummary captured = orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/spa.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null,
                "crud", null, interactions));

        assertThat(captured.artifactId()).isNotBlank();
        assertThat(forwardedInteractions.get()).hasSize(2);
        assertThat(forwardedInteractions.get().get(1).get("value").asText()).isEqualTo("홍길동");
    }

    /** R7(04번 문서 §10): interaction 조합이 다르면(documentKey에 반영되어) 별도 Artifact가 생성된다. */
    @Test
    void t07_differentInteractionsProduceDifferentArtifacts(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<String> lastDocumentKey = new AtomicReference<>();
        HttpServer extractor = extractorServer(exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            lastDocumentKey.set(body.get("documentKey").asText());
            var requestIds = new String[]{body.get("captureId").asText(), body.get("documentKey").asText()};
            byte[] figpack = packUnchecked(mapper, requestIds[0], requestIds[1], "SPA", List.of(
                    field("title", "textbox", "제목", null)));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/spa.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud"));
        String initialDocumentKey = lastDocumentKey.get();

        orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/spa.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null,
                "crud", null, List.of(new com.krdevops.springai.model.capture.InteractionStep(
                        "click", "#reveal", null))));
        String afterClickDocumentKey = lastDocumentKey.get();

        assertThat(afterClickDocumentKey).isNotEqualTo(initialDocumentKey);
    }

    /** R8(04번 문서 §11): 3개 viewport를 순서대로 캡처해 selectorHint 기준으로 대응시킨 Bundle을 만든다. */
    @Test
    void r8_captureMultiViewportBuildsBundleWithComponentMatches(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<String> requestedViewports = new ArrayList<>();
        HttpServer extractor = extractorServer(exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            String viewportName = body.get("viewport").get("name").asText();
            requestedViewports.add(viewportName);
            int viewportWidth = body.get("viewport").get("width").asInt();
            List<RenderedNode> nodes = new ArrayList<>(List.of(
                    node("root", null, null),
                    node("gnb", "root", "nav#gnb")));
            if (!"desktop".equals(viewportName)) {
                nodes.add(node("hamburger", "root", "button.hamburger"));
            }
            byte[] figpack = packWithNodesUnchecked(mapper,
                    body.get("captureId").asText(), body.get("documentKey").asText(),
                    viewportName, viewportWidth, nodes);
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        RenderedDesignBundle bundle = orchestration.captureMultiViewport(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud"));

        assertThat(requestedViewports).containsExactly("desktop", "tablet", "mobile");
        assertThat(bundle.viewportArtifacts()).containsOnlyKeys("desktop", "tablet", "mobile");
        assertThat(bundle.warnings()).isEmpty();
        var gnb = bundle.componentMatches().stream()
                .filter(m -> m.selectorHint().equals("nav#gnb")).findFirst().orElseThrow();
        assertThat(gnb.status()).isEqualTo(com.krdevops.springai.model.capture.ComponentMatch.Status.MATCHED_ALL);
        var hamburger = bundle.componentMatches().stream()
                .filter(m -> m.selectorHint().equals("button.hamburger")).findFirst().orElseThrow();
        assertThat(hamburger.status())
                .isEqualTo(com.krdevops.springai.model.capture.ComponentMatch.Status.HIDDEN_IN_SOME);
        assertThat(bundle.breakpointObservations()).contains(
                new com.krdevops.springai.model.capture.BreakpointObservation("button.hamburger", "desktop",
                        "tablet", com.krdevops.springai.model.capture.BreakpointObservation.Change.SHOWN));
    }

    /** R8(04번 문서 §11): 일부 viewport 캡처가 실패해도(부분 성공) 나머지로 Bundle을 만들고 경고를 남긴다. */
    @Test
    void r8_captureMultiViewportContinuesOnPartialFailure(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HttpServer extractor = extractorServer(exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            String viewportName = body.get("viewport").get("name").asText();
            if ("tablet".equals(viewportName)) {
                byte[] error = "Extractor internal error".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(500, error.length);
                exchange.getResponseBody().write(error);
                return;
            }
            int viewportWidth = body.get("viewport").get("width").asInt();
            byte[] figpack = packWithNodesUnchecked(mapper,
                    body.get("captureId").asText(), body.get("documentKey").asText(),
                    viewportName, viewportWidth, List.of(node("root", null, null)));
            exchange.getResponseHeaders().add("Content-Type", "application/vnd.springai.figpack+zip");
            exchange.sendResponseHeaders(200, figpack.length);
            exchange.getResponseBody().write(figpack);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        RenderedDesignBundle bundle = orchestration.captureMultiViewport(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud"));

        assertThat(bundle.viewportArtifacts()).containsOnlyKeys("desktop", "mobile");
        assertThat(bundle.warnings()).hasSize(1);
        assertThat(bundle.warnings().get(0).code()).isEqualTo("VIEWPORT_CAPTURE_FAILED");
    }

    /** R8(04번 문서 §11): 3개 viewport 모두 실패하면 부분 결과 없이 예외를 던진다. */
    @Test
    void r8_captureMultiViewportFailsWhenAllViewportsFail(@TempDir Path tempDir) throws Exception {
        HttpServer extractor = extractorServer(exchange -> {
            byte[] error = "Extractor internal error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, error.length);
            exchange.getResponseBody().write(error);
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.captureMultiViewport(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storageStateRefMustBeAnExtractorIssuedUuid() {
        assertThatThrownBy(() -> new CaptureWebPageRequest(
                "http://localhost:8080/qna/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null,
                "crud", "plain-text-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    /** R7-T03: extractor가 5xx를 반환하면 캡처가 실패하고, 실패한 캡처는 Artifact를 남기지 않는다. */
    @Test
    void t03_extractorServerErrorFailsCaptureWithoutPersistingArtifact(@TempDir Path tempDir) throws Exception {
        HttpServer extractor = extractorServer(exchange -> {
            byte[] body = "Extractor internal error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
        assertThat(tempDir.resolve("captures")).satisfiesAnyOf(
                path -> assertThat(path).doesNotExist(),
                path -> assertThat(path.toFile().list()).isNullOrEmpty());
    }

    /** R7-T03: extractor 연결 자체가 끊기면(응답 없음) 명확한 오류로 실패하고 조용히 삼켜지지 않는다. */
    @Test
    void t03_extractorConnectionFailureFailsCaptureWithClearError(@TempDir Path tempDir) throws Exception {
        // 요청을 받자마자 아무 응답 없이 연결을 끊어 "extractor가 죽어 있음" 상황을 흉내낸다.
        HttpServer extractor = extractorServer(HttpExchange::close);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("extractor 호출 실패");
    }

    /** R7-T03: URL 검증 실패는 extractor를 호출하기도 전에 차단된다(허용되지 않은 origin). */
    @Test
    void t03_disallowedOriginFailsBeforeCallingExtractor(@TempDir Path tempDir) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer extractor = extractorServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.capture(new CaptureWebPageRequest(
                "http://evil.example.com/list.do", CaptureProfile.LOCAL_WEB, ViewportSpec.desktop(), null, "crud")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requestCount).hasValue(0);
    }

    /**
     * R6(04번 문서 §9): 세션 생성이 원문 username/password를 extractor에 그대로 전달하고,
     * 발급된 opaque sessionId/expiresAt을 그대로 돌려준다(springai가 값을 가공하지 않음).
     */
    @Test
    void r6_createSessionForwardsCredentialsAndReturnsOpaqueSessionId(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<com.fasterxml.jackson.databind.JsonNode> receivedBody = new AtomicReference<>();
        HttpServer extractor = extractorServer("/v1/sessions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Extractor-Key")).isEqualTo("extractor-secret");
            receivedBody.set(mapper.readTree(exchange.getRequestBody()));
            byte[] body = mapper.writeValueAsBytes(new com.krdevops.springai.model.capture.WebCaptureSessionResponse(
                    "11111111-1111-4111-8111-111111111111", "2026-08-19T00:00:00Z"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        var response = orchestration.createSession(new com.krdevops.springai.model.capture.WebCaptureSessionRequest(
                "http://localhost:8080/login.do", null, "#username", "e2e-user",
                "#password", "e2e-pass", "#submit", "#dashboard", 15000));

        assertThat(response.sessionId()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(response.expiresAt()).isEqualTo("2026-08-19T00:00:00Z");
        var body = receivedBody.get();
        assertThat(body.get("username").asText()).isEqualTo("e2e-user");
        assertThat(body.get("password").asText()).isEqualTo("e2e-pass");
        assertThat(body.get("loginUrl").asText()).isEqualTo("http://localhost:8080/login.do");
        assertThat(body.get("allowedOrigins")).anySatisfy(
                value -> assertThat(value.asText()).isEqualTo("http://localhost:8080"));
    }

    /** R6(04번 문서 §9): extractor가 SESSION_LOGIN_FAILED를 반환하면 명확한 오류로 실패한다. */
    @Test
    void r6_createSessionFailsClearlyWhenExtractorRejectsLogin(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HttpServer extractor = extractorServer("/v1/sessions", exchange -> {
            byte[] body = "{\"error\":\"CAPTURE_AUTH_FAILED\",\"code\":\"CAPTURE_AUTH_FAILED\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
        });
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.createSession(
                new com.krdevops.springai.model.capture.WebCaptureSessionRequest(
                        "http://localhost:8080/login.do", null, "#username", "e2e-user",
                        "#password", "wrong-pass", "#submit", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401");
    }

    /** R6(04번 문서 §9): loginUrl도 capture()와 동일하게 허용 origin만 통과한다. */
    @Test
    void r6_createSessionRejectsDisallowedLoginOrigin(@TempDir Path tempDir) throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer extractor = extractorServer("/v1/sessions", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
        });
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties(tempDir, extractor);
        WebCaptureOrchestrationService orchestration = orchestration(mapper, properties);

        assertThatThrownBy(() -> orchestration.createSession(
                new com.krdevops.springai.model.capture.WebCaptureSessionRequest(
                        "http://evil.example.com/login.do", null, "#username", "e2e-user",
                        "#password", "e2e-pass", "#submit", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requestCount).hasValue(0);
    }

    private WebCaptureOrchestrationService orchestration(ObjectMapper mapper, WebCaptureProperties properties) {
        ExternalCallGuard guard = new ExternalCallGuard(new OperationalResilienceProperties());
        WebCaptureClient client = new WebCaptureClient(properties, mapper, guard);
        RenderedDesignDocumentValidator documentValidator = new RenderedDesignDocumentValidator(mapper);
        RenderedDesignPackageValidator packageValidator =
                new RenderedDesignPackageValidator(mapper, properties, documentValidator);
        return new WebCaptureOrchestrationService(properties, new WebCaptureUrlValidator(properties), client,
                packageValidator, artifactService(properties, mapper));
    }

    private DesignArtifactService artifactService(WebCaptureProperties properties, ObjectMapper mapper) {
        return new DesignArtifactService(properties, mapper);
    }

    private WebCaptureProperties properties(Path tempDir, HttpServer extractor) {
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setEnabled(true);
        properties.setExtractorApiKey("extractor-secret");
        properties.setDocumentKeySecret("document-secret");
        properties.setArtifactBasePath(tempDir);
        properties.setAllowedOrigins(List.of("http://localhost:8080"));
        properties.setExtractorBaseUrl(URI.create("http://127.0.0.1:" + extractor.getAddress().getPort()));
        properties.setResponseTimeoutSeconds(3);
        properties.setConnectTimeoutSeconds(2);
        return properties;
    }

    private HttpServer extractorServer(ExchangeHandler handler) throws IOException {
        return extractorServer("/v1/captures", handler);
    }

    private HttpServer extractorServer(String path, ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
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

    private record Field(String key, String role, String value, String label) {
    }

    private Field field(String key, String role, String value, String label) {
        return new Field(key, role, value, label);
    }

    /** R8(04번 문서 §11) 테스트 전용: selectorHint를 직접 지정한 노드를 만든다. */
    private static RenderedNode node(String id, String parentId, String selectorHint) {
        var bounds = new RenderedNode.Bounds(0, 0, 10, 10);
        return new RenderedNode(id, parentId, "ELEMENT", "div", null, null, null, null,
                true, bounds, Map.of(), List.of(), selectorHint, 0, 0.0, null);
    }

    private byte[] packWithNodesUnchecked(ObjectMapper mapper, String captureId, String documentKey,
            String viewportName, int viewportWidth, List<RenderedNode> nodes) {
        try {
            return packWithNodes(mapper, captureId, documentKey, viewportName, viewportWidth, nodes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** R8(04번 문서 §11) 테스트 전용: viewport/노드를 직접 지정해 figpack을 만든다(선택 컬럼·필드값이 아닌 selectorHint/부모 관계 검증용). */
    private byte[] packWithNodes(ObjectMapper mapper, String captureId, String documentKey,
            String viewportName, int viewportWidth, List<RenderedNode> nodes) throws Exception {
        RenderedDesignDocumentValidator validator = new RenderedDesignDocumentValidator(mapper);
        RenderedDesignDocument base = new RenderedDesignDocument(RenderedDesignDocument.SCHEMA_VERSION,
                captureId, documentKey, "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "UNKNOWN",
                        "http://localhost:8080/page.do", "http://localhost:8080/page.do",
                        "c".repeat(64), "2026-08-20T00:00:00Z"),
                new RenderedDesignDocument.Environment(viewportName, viewportWidth, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page(viewportName, "root", viewportWidth, 1200, 0, 0, "white"),
                nodes, List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of("name", "test"));
        String hash = validator.calculateContentHash(base);
        RenderedDesignDocument document = new RenderedDesignDocument(base.schemaVersion(), captureId,
                documentKey, hash, base.source(), base.environment(), base.page(), base.nodes(), base.assets(),
                base.tokens(), base.componentCandidates(), base.interactions(), base.warnings(), base.extractor());
        byte[] documentBytes = mapper.writeValueAsBytes(document);
        byte[] preview = new byte[]{1, 2, 3};
        var manifest = new RenderedDesignPackageManifest(RenderedDesignPackageManifest.PACKAGE_VERSION,
                RenderedDesignPackageManifest.MIME_TYPE, captureId, documentKey, hash, List.of(
                new RenderedDesignPackageManifest.Entry("document.json", documentBytes.length, sha(documentBytes)),
                new RenderedDesignPackageManifest.Entry("preview.png", preview.length, sha(preview))));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("document.json", documentBytes);
        entries.put("preview.png", preview);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    /** WebCaptureClient가 실제로 보낸 요청 본문에서 captureId/documentKey를 읽는다(index 0/1). */
    private String[] readCaptureIds(ObjectMapper mapper, HttpExchange exchange) throws IOException {
        var body = mapper.readTree(exchange.getRequestBody());
        return new String[]{body.get("captureId").asText(), body.get("documentKey").asText()};
    }

    private byte[] packUnchecked(
            ObjectMapper mapper, String captureId, String documentKey, String pageTitle, List<Field> fields) {
        try {
            return pack(mapper, captureId, documentKey, pageTitle, fields);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] pack(
            ObjectMapper mapper, String captureId, String documentKey, String pageTitle, List<Field> fields)
            throws Exception {
        RenderedDesignDocumentValidator validator = new RenderedDesignDocumentValidator(mapper);
        var bounds = new RenderedNode.Bounds(0, 0, 100, 30);
        List<RenderedNode> nodes = new ArrayList<>();
        List<String> rootChildren = new ArrayList<>();
        for (Field f : fields) rootChildren.add(f.key());
        nodes.add(new RenderedNode("root", null, "ELEMENT", "html", "", null, null, null,
                true, bounds, Map.of(), rootChildren));
        for (Field f : fields) {
            boolean isButton = f.role().equals("button");
            nodes.add(new RenderedNode(f.key(), "root", isButton ? "BUTTON" : "ELEMENT",
                    isButton ? "button" : "input", f.role(), f.value(), f.label(), null,
                    true, bounds, Map.of(), List.of()));
        }
        RenderedDesignDocument base = new RenderedDesignDocument(RenderedDesignDocument.SCHEMA_VERSION,
                captureId, documentKey, "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "UNKNOWN",
                        "http://localhost:8080/page.do", "http://localhost:8080/page.do",
                        "c".repeat(64), "2026-08-17T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page(pageTitle, "root", 1440, 1200, 0, 0, "white"),
                nodes, List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of("name", "test"));
        String hash = validator.calculateContentHash(base);
        RenderedDesignDocument document = new RenderedDesignDocument(base.schemaVersion(), captureId,
                documentKey, hash, base.source(), base.environment(), base.page(), base.nodes(), base.assets(),
                base.tokens(), base.componentCandidates(), base.interactions(), base.warnings(), base.extractor());
        byte[] documentBytes = mapper.writeValueAsBytes(document);
        byte[] preview = new byte[]{1, 2, 3};
        var manifest = new RenderedDesignPackageManifest(RenderedDesignPackageManifest.PACKAGE_VERSION,
                RenderedDesignPackageManifest.MIME_TYPE, captureId, documentKey, hash, List.of(
                new RenderedDesignPackageManifest.Entry("document.json", documentBytes.length, sha(documentBytes)),
                new RenderedDesignPackageManifest.Entry("preview.png", preview.length, sha(preview))));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("document.json", documentBytes);
        entries.put("preview.png", preview);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String sha(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
