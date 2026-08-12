package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public record ComponentResolutionContext(
        ScreenPattern pattern,
        FigmaScreenType screenType,
        Platform platform,
        LayoutDensity density,
        FieldMode mode,
        ComponentState state,
        Boolean required,
        Boolean disabled,
        Integer fieldCount,
        SemanticRole role
) {
    public ComponentResolutionContext {
        if (pattern == null || platform == null || role == null) {
            throw new IllegalArgumentException("pattern, platform, role은 필수입니다.");
        }
    }

    public Map<String, String> asRuleContext() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "pattern", pattern.code());
        put(values, "screenType", screenType);
        put(values, "platform", platform);
        put(values, "density", density);
        put(values, "mode", mode);
        put(values, "state", state);
        put(values, "required", required);
        put(values, "disabled", disabled);
        put(values, "fieldCount", fieldCount);
        put(values, "role", role.code());
        return Map.copyOf(values);
    }

    public String stableHash() {
        try {
            String canonical = asRuleContext().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((a, b) -> a + "&" + b).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Context Hash 생성 실패", exception);
        }
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null) values.put(key, value.toString());
    }
}
