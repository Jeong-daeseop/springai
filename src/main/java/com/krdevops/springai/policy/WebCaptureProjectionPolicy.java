package com.krdevops.springai.policy;

import com.krdevops.springai.model.capture.ComponentCandidate;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import com.krdevops.springai.model.capture.SafeDesignProjection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class WebCaptureProjectionPolicy {
    private static final Set<String> LABEL_NODE_TYPES = Set.of("LABEL", "BUTTON", "TH", "HEADING");
    private static final Set<String> CONTROL_TAGS = Set.of("input", "select", "textarea");

    public SafeDesignProjection project(RenderedDesignDocument document) {
        List<SafeDesignProjection.SafeComponent> components = document.componentCandidates().stream()
                .map(this::component)
                .toList();
        List<SafeDesignProjection.SafeField> fields = new ArrayList<>();
        List<SafeDesignProjection.SafeAction> actions = new ArrayList<>();
        for (RenderedNode node : document.nodes()) {
            String tag = normalize(node.tag());
            String type = normalize(node.type()).toUpperCase(Locale.ROOT);
            String safeLabel = approvedLabel(node, type);
            if (CONTROL_TAGS.contains(tag) && safeLabel != null) {
                fields.add(new SafeDesignProjection.SafeField(node.id(), safeLabel,
                        inferFieldRole(safeLabel), tag, 0.8));
            }
            if (("button".equals(tag) || "BUTTON".equals(type)) && safeLabel != null) {
                actions.add(new SafeDesignProjection.SafeAction(safeLabel, normalize(node.role())));
            }
        }
        int viewportWidth = document.environment() == null ? 0 : document.environment().viewportWidth();
        double documentWidth = document.page() == null ? 0 : document.page().documentWidth();
        List<String> warnings = document.warnings().stream().map(warning -> warning.code()).toList();
        String pageTitle = document.page() == null ? null : sanitizeLabel(document.page().title());
        return new SafeDesignProjection(pageTitle, viewportWidth, documentWidth,
                components, fields, actions, document.tokens(), warnings);
    }

    private SafeDesignProjection.SafeComponent component(ComponentCandidate candidate) {
        return new SafeDesignProjection.SafeComponent(candidate.type(), candidate.confidence(), candidate.evidence());
    }

    private String approvedLabel(RenderedNode node, String type) {
        if (!LABEL_NODE_TYPES.contains(type) && !CONTROL_TAGS.contains(normalize(node.tag()))) return null;
        String label = sanitizeLabel(node.label());
        if (label == null && LABEL_NODE_TYPES.contains(type)) label = sanitizeLabel(node.text());
        if (label == null || SensitiveFieldPolicy.isSensitiveDisplayField(node.id(), label)) return null;
        return label;
    }

    private String sanitizeLabel(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank() || normalized.length() > 100 || normalized.contains("@")
                || normalized.matches(".*\\d{3}[- ]?\\d{3,4}[- ]?\\d{4}.*")) return null;
        return normalized;
    }

    private String inferFieldRole(String label) {
        String value = label.toUpperCase(Locale.ROOT);
        if (value.contains("제목") || value.contains("TITLE")) return "TITLE";
        if (value.contains("내용") || value.contains("CONTENT")) return "CONTENT";
        if (value.contains("상태") || value.contains("STATUS")) return "STATUS";
        if (value.contains("작성자") || value.contains("AUTHOR")) return "AUTHOR";
        if (value.contains("부서") || value.contains("DEPARTMENT")) return "DEPARTMENT";
        if (value.contains("등록일") || value.contains("CREATED")) return "CREATED_AT";
        return "GENERIC";
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
