package com.krdevops.springai.model.capture;

import java.util.List;
import java.util.Map;

public record RenderedNode(
        String id, String parentId, String type, String tag, String role,
        String label, String text, String value, boolean visible,
        Bounds bounds, Map<String, String> styles, List<String> children) {
    public RenderedNode {
        styles = styles == null ? Map.of() : Map.copyOf(styles);
        children = children == null ? List.of() : List.copyOf(children);
    }
    public record Bounds(double x, double y, double width, double height) {}
}
