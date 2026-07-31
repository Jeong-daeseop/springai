package com.krdevops.springai.model.capture;

import java.util.List;
import java.util.Map;

public record RenderedNode(
        String id, String parentId, String type, String tag, String role,
        String label, String text, String value, boolean visible,
        Bounds bounds, Map<String, String> styles, List<String> children,
        String selectorHint, int sourceOrder, double rotation, String name) {
    public RenderedNode {
        styles = styles == null ? Map.of() : Map.copyOf(styles);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** 신규 §5.5 필드(selectorHint/sourceOrder/rotation/name) 도입 전 호출자 호환용. */
    public RenderedNode(String id, String parentId, String type, String tag, String role,
            String label, String text, String value, boolean visible,
            Bounds bounds, Map<String, String> styles, List<String> children) {
        this(id, parentId, type, tag, role, label, text, value, visible, bounds, styles, children,
                null, 0, 0.0, label);
    }

    public record Bounds(double x, double y, double width, double height) {}
}
