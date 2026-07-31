package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * R0-002/R2-011: {pageId}/{section}/{fieldId} 형태의 결정론적 logicalNodeId를 만든다(11번 §6).
 * 각 경로 세그먼트는 공통 JSON Schema의 logicalNodeId 규칙과 동일하게 검증한다.
 */
@Component
public class LogicalNodeIdFactory {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    public String page(String pageId) {
        return segment("pageId", pageId);
    }

    public String section(String pageId, String section) {
        return page(pageId) + "/" + path("section", section);
    }

    public String field(String pageId, String section, String fieldId) {
        return section(pageId, section) + "/" + segment("fieldId", fieldId);
    }

    public String action(String pageId, String actionType) {
        String normalizedAction = segment("actionType", actionType).toLowerCase(Locale.ROOT);
        return page(pageId) + "/action/" + normalizedAction;
    }

    private String segment(String name, String value) {
        if (value == null || !SEGMENT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name
                    + "는 영문자 또는 숫자로 시작하고 영문자·숫자·._:-만 포함해야 합니다: " + value);
        }
        return value;
    }

    private String path(String name, String value) {
        if (value == null) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
        String[] segments = value.split("/", -1);
        for (String candidate : segments) {
            segment(name, candidate);
        }
        return String.join("/", segments);
    }
}
