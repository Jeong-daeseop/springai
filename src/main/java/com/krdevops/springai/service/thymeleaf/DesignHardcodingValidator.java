package com.krdevops.springai.service.thymeleaf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** R6-063: DESIGN Token을 우회하는 생성 HTML의 명시적 하드코딩을 차단한다. */
public final class DesignHardcodingValidator {
    private static final Pattern INLINE_STYLE = Pattern.compile("(?is)\\bstyle\\s*=\\s*([\\\"'])(.*?)\\1");
    private static final Pattern RAW_COLOR = Pattern.compile("(?i)(#[0-9a-f]{3,8}\\b|rgba?\\s*\\(|hsla?\\s*\\(|\\b(?:white|black|red|blue|green)\\b)");

    public List<String> validate(String html) {
        if (html == null || html.isBlank()) return List.of();
        List<String> issues = new ArrayList<>();
        Matcher matcher = INLINE_STYLE.matcher(html);
        while (matcher.find()) {
            if (RAW_COLOR.matcher(matcher.group(2)).find()) {
                issues.add("DESIGN_TOKEN_HARDCODED: inline style에 원시 색상값이 있습니다(Design Token/CSS class를 사용하세요).");
            }
        }
        return List.copyOf(issues);
    }
}
