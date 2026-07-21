package com.krdevops.springai.model.capture;

import java.util.List;
import java.util.Map;

/** 실제 업무 값이 구조적으로 존재하지 않는 UiDesignSpec 매핑 전용 모델. */
public record SafeDesignProjection(
        String pageTitle, int viewportWidth, double documentWidth,
        List<SafeComponent> components, List<SafeField> fields,
        List<SafeAction> actions, Map<String, String> tokens, List<String> warnings) {
    public SafeDesignProjection {
        components = components == null ? List.of() : List.copyOf(components);
        fields = fields == null ? List.of() : List.copyOf(fields);
        actions = actions == null ? List.of() : List.copyOf(actions);
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
    public record SafeComponent(String type, double confidence, List<String> evidence) {
        public SafeComponent { evidence = evidence == null ? List.of() : List.copyOf(evidence); }
    }
    public record SafeField(String id, String label, String role, String control, double confidence) {}
    public record SafeAction(String label, String role) {}
}
