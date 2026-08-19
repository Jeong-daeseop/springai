package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import com.krdevops.springai.model.capture.InteractionStep;
import com.krdevops.springai.model.capture.WebCaptureSessionRequest;
import com.krdevops.springai.model.capture.WebCaptureSessionResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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
        if (!properties.isEnabled()) throw new IllegalStateException("WEB_CAPTURE 기능이 비활성 상태입니다.");
        WebCaptureUrlValidator.ValidatedUrl url = urlValidator.validate(request.url());
        String captureId = UUID.randomUUID().toString();
        String documentKey = documentKey(url, request);
        byte[] bytes = client.capture(request, url.uri(), captureId, documentKey);
        RenderedDesignPackageValidator.ValidatedPackage pack =
                packageValidator.validate(bytes, captureId, documentKey);
        urlValidator.validate(pack.document().source().finalUrl());
        return artifactService.save(pack);
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
