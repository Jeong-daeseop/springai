package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureWarning;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.InteractionStep;
import com.krdevops.springai.model.capture.RenderedDesignBundle;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.ViewportSpec;
import com.krdevops.springai.model.capture.WebCaptureSessionRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WebCaptureOrchestrationService {
    private final WebCaptureProperties properties;
    private final WebCaptureUrlValidator urlValidator;
    private final WebCaptureClient client;
    private final RenderedDesignPackageValidator packageValidator;
    private final DesignArtifactService artifactService;

    public WebCaptureOrchestrationService(WebCaptureProperties properties,
            WebCaptureUrlValidator urlValidator, WebCaptureClient client,
            RenderedDesignPackageValidator packageValidator, DesignArtifactService artifactService) {
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.client = client;
        this.packageValidator = packageValidator;
        this.artifactService = artifactService;
    }

    public CaptureArtifactSummary capture(CaptureWebPageRequest request) {
        return captureInternal(request).summary();
    }

    /**
     * R8(04번 문서 §11): Desktop/Tablet/Mobile 세 viewport를 순서대로 캡처해 하나의
     * {@link RenderedDesignBundle}로 묶는다. 개별 viewport 실패는 {@link CaptureWarning}으로
     * 기록하고 계속 진행하며("부분 성공 상태 처리"), 최소 1개 viewport가 성공하면 Bundle을
     * 반환한다. 3개 모두 실패한 경우에만 예외를 던진다.
     */
    public RenderedDesignBundle captureMultiViewport(CaptureWebPageRequest baseRequest) {
        if (!properties.isEnabled()) throw new IllegalStateException("WEB_CAPTURE 기능이 비활성 상태입니다.");
        Map<String, ViewportSpec> viewports = new LinkedHashMap<>();
        viewports.put("desktop", ViewportSpec.desktop());
        viewports.put("tablet", ViewportSpec.tablet());
        viewports.put("mobile", ViewportSpec.mobile());

        Map<String, String> viewportArtifacts = new LinkedHashMap<>();
        Map<String, RenderedDesignDocument> documentsByViewport = new LinkedHashMap<>();
        List<CaptureWarning> warnings = new ArrayList<>();
        for (var entry : viewports.entrySet()) {
            CaptureWebPageRequest perViewportRequest = new CaptureWebPageRequest(baseRequest.url(),
                    baseRequest.profile(), entry.getValue(), baseRequest.readiness(), baseRequest.featureType(),
                    baseRequest.storageStateRef(), baseRequest.interactions());
            try {
                CaptureResult result = captureInternal(perViewportRequest);
                viewportArtifacts.put(entry.getKey(), result.summary().artifactId());
                documentsByViewport.put(entry.getKey(), result.document());
            } catch (Exception e) {
                warnings.add(new CaptureWarning("VIEWPORT_CAPTURE_FAILED", null,
                        entry.getKey() + " viewport 캡처 실패: " + e.getMessage()));
            }
        }
        if (documentsByViewport.isEmpty()) {
            throw new IllegalStateException("모든 viewport 캡처에 실패했습니다: " + warnings);
        }

        MultiViewportComponentMatcher.Result analysis = MultiViewportComponentMatcher.analyze(documentsByViewport);
        return new RenderedDesignBundle(RenderedDesignBundle.SCHEMA_VERSION, UUID.randomUUID().toString(),
                viewportArtifacts, analysis.componentMatches(), analysis.breakpointObservations(), warnings);
    }

    private record CaptureResult(CaptureArtifactSummary summary, RenderedDesignDocument document) {
    }

    private CaptureResult captureInternal(CaptureWebPageRequest request) {
        if (!properties.isEnabled()) throw new IllegalStateException("WEB_CAPTURE 기능이 비활성 상태입니다.");
        WebCaptureUrlValidator.ValidatedUrl url = urlValidator.validate(request.url());
        String captureId = UUID.randomUUID().toString();
        String documentKey = documentKey(url, request);
        byte[] bytes = client.capture(request, url.uri(), captureId, documentKey);
        RenderedDesignPackageValidator.ValidatedPackage pack =
                packageValidator.validate(bytes, captureId, documentKey);
        urlValidator.validate(pack.document().source().finalUrl());
        return new CaptureResult(artifactService.save(pack), pack.document());
    }

    /**
     * 04번 문서 R6(§9): 인증 세션(로그인)을 발급한다. {@link com.krdevops.springai.controller
     * .WebCaptureSessionController}(운영자 전용 REST)에서만 호출된다 — MCP Tool 경로에는 노출하지
     * 않는다(원문 username/password가 LLM에 전달·로깅되는 것을 원천 차단).
     */
    public WebCaptureSessionResponse createSession(WebCaptureSessionRequest request) {
        if (!properties.isEnabled()) throw new IllegalStateException("WEB_CAPTURE 기능이 비활성 상태입니다.");
        WebCaptureUrlValidator.ValidatedUrl url = urlValidator.validate(request.loginUrl());
        return client.createSession(request, url.uri());
    }

    private String documentKey(WebCaptureUrlValidator.ValidatedUrl url, CaptureWebPageRequest request) {
        try {
            String canonical = url.origin() + url.uri().getPath() + "\n" + request.profile()
                    + "\n" + request.viewport().name() + ":" + request.viewport().width()
                    + "x" + request.viewport().height() + "\n" + interactionState(request);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDocumentKeySecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("documentKey 생성 실패", e);
        }
    }

    /**
     * R7(04번 문서 §10): interaction step이 없는 기존 호출은 documentKey가 이전과 동일하게
     * "initial"로 고정돼(Release 1 회귀 없음), 서로 다른 interaction 순서는 서로 다른
     * documentKey(= 서로 다른 SPA 상태의 artifact)를 갖는다.
     */
    private String interactionState(CaptureWebPageRequest request) {
        if (request.interactions() == null || request.interactions().isEmpty()) return "initial";
        return request.interactions().stream()
                .map(InteractionStep::toString)
                .collect(Collectors.joining("|"));
    }
}
