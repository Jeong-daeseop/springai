package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I-6C: Figma 디자인 시스템 기반 Thymeleaf CSS auto-patch.
 */
@Service
public class FigmaUpdateApplier {

    private static final Pattern COLOR_VAR_PATTERN = Pattern.compile("--color-([a-z-]+)");
    private static final Pattern TYPOGRAPHY_VAR_PATTERN = Pattern.compile("--typography-([a-z-]+)");
    private static final Pattern SPACING_VAR_PATTERN = Pattern.compile("--spacing-([a-z0-9-]+)");

    /**
     * Figma 디자인 토큰으로부터 CSS 변수를 생성합니다.
     */
    public String generateCssFromDesignTokens(Map<String, String> designTokens) {
        StringBuilder css = new StringBuilder(":root {\n");

        designTokens.forEach((key, value) -> {
            css.append("  --").append(key).append(": ").append(value).append(";\n");
        });

        css.append("}\n");
        return css.toString();
    }

    /**
     * 기존 Thymeleaf CSS를 Figma 디자인 토큰으로 업데이트합니다.
     */
    public String patchCssWithDesignTokens(String cssContent, Map<String, String> designTokens) {
        String patchedCss = cssContent;

        // 색상 토큰 적용
        for (Map.Entry<String, String> entry : designTokens.entrySet()) {
            if (entry.getKey().startsWith("color-")) {
                patchedCss = patchedCss.replaceAll(
                    Pattern.quote(entry.getValue()),
                    "var(--" + entry.getKey() + ")"
                );
            }
        }

        // 타이포그래피 토큰 적용
        for (Map.Entry<String, String> entry : designTokens.entrySet()) {
            if (entry.getKey().startsWith("typography-")) {
                patchedCss = patchedCss.replaceAll(
                    Pattern.quote(entry.getValue()),
                    "var(--" + entry.getKey() + ")"
                );
            }
        }

        return patchedCss;
    }

    /**
     * Figma frame에 스타일을 적용합니다.
     */
    public Map<String, Object> applyStylesToFigmaFrame(
            String figmaNodeId,
            String figmaPaint,
            String figmaStroke,
            String figmaEffects) {

        Map<String, Object> styleUpdate = new HashMap<>();
        styleUpdate.put("nodeId", figmaNodeId);
        styleUpdate.put("paint", figmaPaint);
        styleUpdate.put("stroke", figmaStroke);
        styleUpdate.put("effects", figmaEffects);
        styleUpdate.put("appliedAt", System.currentTimeMillis());

        return styleUpdate;
    }

    /**
     * CSS 선택자를 Figma 컴포넌트 스타일에 매핑합니다.
     */
    public Map<String, String> mapCssSelectorToFigmaStyle(String cssSelector, String cssDeclaration) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cssSelector", cssSelector);
        mapping.put("cssDeclaration", cssDeclaration);

        // 예: .button → Figma Button component style
        if (cssSelector.contains("button")) {
            mapping.put("figmaComponent", "Button");
        } else if (cssSelector.contains("input")) {
            mapping.put("figmaComponent", "TextInput");
        } else if (cssSelector.contains("table")) {
            mapping.put("figmaComponent", "Table");
        }

        mapping.put("mappedAt", String.valueOf(System.currentTimeMillis()));
        return mapping;
    }

    /**
     * CSS 변수를 Figma design token JSON으로 변환합니다.
     */
    public String convertCssVarsToFigmaTokenJson(String cssContent) {
        StringBuilder jsonTokens = new StringBuilder("{\n  \"tokens\": {\n");

        // 색상 추출
        Matcher colorMatcher = COLOR_VAR_PATTERN.matcher(cssContent);
        boolean first = true;
        while (colorMatcher.find()) {
            if (!first) jsonTokens.append(",\n");
            jsonTokens.append("    \"").append(colorMatcher.group(1)).append("\": \"#...\"\n");
            first = false;
        }

        // 타이포그래피 추출
        Matcher typographyMatcher = TYPOGRAPHY_VAR_PATTERN.matcher(cssContent);
        while (typographyMatcher.find()) {
            if (!first) jsonTokens.append(",\n");
            jsonTokens.append("    \"").append(typographyMatcher.group(1)).append("\": {...}\n");
            first = false;
        }

        jsonTokens.append("  }\n}");
        return jsonTokens.toString();
    }
}
