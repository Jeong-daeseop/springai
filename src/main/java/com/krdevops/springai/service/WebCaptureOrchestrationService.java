package com.krdevops.springai.service;

import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.CaptureArtifactSummary;
import com.krdevops.springai.model.capture.CaptureWebPageRequest;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

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

    private String documentKey(WebCaptureUrlValidator.ValidatedUrl url, CaptureWebPageRequest request) {
        try {
            String canonical = url.origin() + url.uri().getPath() + "\n" + request.profile()
                    + "\n" + request.viewport().name() + ":" + request.viewport().width()
                    + "x" + request.viewport().height() + "\ninitial";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getDocumentKeySecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("documentKey 생성 실패", e);
        }
    }
}
