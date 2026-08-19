package com.krdevops.springai.model.capture;

import java.util.List;
import java.util.Map;

public record RenderedDesignDocument(
        String schemaVersion, String captureId, String documentKey, String contentHash,
        Source source, Environment environment, Page page, List<RenderedNode> nodes,
        List<RenderedAsset> assets, Map<String, String> tokens,
        List<ComponentCandidate> componentCandidates, List<Map<String, String>> interactions,
        List<CaptureWarning> warnings, Map<String, String> extractor) {
    public static final String SCHEMA_VERSION = "rendered-design-document-v1";

    public RenderedDesignDocument {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        assets = assets == null ? List.of() : List.copyOf(assets);
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
        componentCandidates = componentCandidates == null ? List.of() : List.copyOf(componentCandidates);
        interactions = interactions == null ? List.of() : List.copyOf(interactions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        extractor = extractor == null ? Map.of() : Map.copyOf(extractor);
    }

    public record Source(String type, String applicationKind, String requestedUrl,
                         String finalUrl, String urlFingerprint, String capturedAt, String stateId) {
        /**
         * R7(04번 문서 §10) {@code stateId} 필드 도입 전 호출자 호환용. 빈 interaction 배열의
         * 결정론적 stateId({@code sha256(JSON.stringify(canonical([])))}, extractor의
         * {@code server.ts}와 동일한 계산)로 채운다.
         */
        public Source(String type, String applicationKind, String requestedUrl,
                String finalUrl, String urlFingerprint, String capturedAt) {
            this(type, applicationKind, requestedUrl, finalUrl, urlFingerprint, capturedAt,
                    "4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945");
        }
    }
    public record Environment(String viewportName, int viewportWidth, int viewportHeight,
                              double deviceScaleFactor, String locale, String timezone,
                              String colorScheme, boolean reducedMotion, String browserEngine) {}
    public record Page(String title, String rootNodeId, double documentWidth,
                       double documentHeight, double scrollX, double scrollY, String backgroundColor) {}
}
