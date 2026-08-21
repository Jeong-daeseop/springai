package com.krdevops.springai.service.thymeleaf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R6-063: 생성 HTML의 inline 디자인 값을 회사 Design Token으로 우회하지 못하게 한다.
 *
 * <p>검사 범위는 의도적으로 생성 산출물의 {@code style} 속성으로 한정한다. 외부 회사 CSS,
 * class 이름, JavaScript/Java 소스는 이 Gate의 대상이 아니다. CSS variable({@code var(...)}),
 * {@code calc(...)}와 {@code inherit}/{@code currentColor} 같은 참조·상속 값은 허용하고,
 * inline에 직접 적힌 색상·간격·타이포그래피·radius·shadow 리터럴만 차단한다.
 */
public final class DesignHardcodingValidator {
    private static final Pattern INLINE_STYLE = Pattern.compile("(?is)\\bstyle\\s*=\\s*([\\\"'])(.*?)\\1");
    private static final Pattern RAW_COLOR = Pattern.compile("(?i)(#[0-9a-f]{3,8}\\b|rgba?\\s*\\(|hsla?\\s*\\(|\\b(?:white|black|red|blue|green|transparent)\\b)");
    private static final Pattern NUMERIC_LENGTH = Pattern.compile("(?i)(?:^|\\s|,)(-?(?:\\d+(?:\\.\\d+)?|\\.\\d+))(?:px|rem|em|pt|vh|vw|%)\\b");
    private static final Pattern DECLARATION = Pattern.compile("(?is)([a-z-]+)\\s*:\\s*([^;]+)");

    private static final java.util.Set<String> COLOR_PROPERTIES = java.util.Set.of(
            "color", "background", "background-color", "border-color", "outline-color", "text-decoration-color");
    private static final java.util.Set<String> LENGTH_PROPERTIES = java.util.Set.of(
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left", "gap",
            "row-gap", "column-gap", "font-size", "letter-spacing", "border-radius");

    public List<String> validate(String html) {
        if (html == null || html.isBlank()) return List.of();
        List<String> issues = new ArrayList<>();
        Matcher matcher = INLINE_STYLE.matcher(html);
        while (matcher.find()) {
            Matcher declaration = DECLARATION.matcher(matcher.group(2));
            while (declaration.find()) {
                String property = declaration.group(1).toLowerCase(java.util.Locale.ROOT);
                String value = declaration.group(2).trim();
                if (isReferenceValue(value) || "none".equalsIgnoreCase(value)) continue;
                if (COLOR_PROPERTIES.contains(property) && RAW_COLOR.matcher(value).find()) {
                    issues.add("DESIGN_TOKEN_HARDCODED: inline style의 " + property
                            + "에 원시 색상값이 있습니다(Design Token/CSS class를 사용하세요).");
                } else if (LENGTH_PROPERTIES.contains(property) && NUMERIC_LENGTH.matcher(value).find()) {
                    issues.add("DESIGN_TOKEN_HARDCODED: inline style의 " + property
                            + "에 원시 길이값이 있습니다(Design Token/CSS class를 사용하세요).");
                } else if ("box-shadow".equals(property) && !isReferenceValue(value)) {
                    issues.add("DESIGN_TOKEN_HARDCODED: inline style에 원시 shadow 값이 있습니다(Design Token/CSS class를 사용하세요).");
                }
            }
        }
        return List.copyOf(issues);
    }

    private boolean isReferenceValue(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("var(") || normalized.contains("calc(")
                || normalized.contains("inherit") || normalized.contains("currentcolor")
                || normalized.contains("initial") || normalized.contains("revert");
    }
}
