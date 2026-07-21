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
                         String finalUrl, String urlFingerprint, String capturedAt) {}
    public record Environment(String viewportName, int viewportWidth, int viewportHeight,
                              double deviceScaleFactor, String locale, String timezone,
                              String colorScheme, boolean reducedMotion, String browserEngine) {}
    public record Page(String title, String rootNodeId, double documentWidth,
                       double documentHeight, double scrollX, double scrollY, String backgroundColor) {}
}
