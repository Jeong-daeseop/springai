package com.krdevops.springai.model.thymeleaf;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * I-4C: HTML Skeleton (구조 + Token, 업무 필드 바인딩 없음).
 * 디자인 시스템 토큰과 컴포넌트 구조만 포함하며, 임의의 th:field를 추가하지 않는다.
 * 기존 ThymeleafSkeleton과 구분하기 위해 명명.
 */
public record ScreenHtmlSkeleton(
        String id,
        String screenName,
        String archetype,           // LIST, FORM, DETAIL
        String htmlStructure,       // layout, fragments, slots (th:field 없음)
        Map<String, String> cssVariables,  // --color-primary, --spacing-md 등
        List<String> slotPlaceholders,     // 예: form-fields, list-rows, search-panel
        List<String> issues,
        LocalDateTime createdAt
) {
    public ScreenHtmlSkeleton {
        cssVariables = cssVariables == null ? Map.of() : Map.copyOf(cssVariables);
        slotPlaceholders = slotPlaceholders == null ? List.of() : List.copyOf(slotPlaceholders);
        issues = issues == null ? List.of() : List.copyOf(issues);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public boolean isValid() {
        return htmlStructure != null && !htmlStructure.isEmpty()
            && !slotPlaceholders.isEmpty()
            && !cssVariables.isEmpty();
    }

    public boolean hasNoArbitraryFieldBindings() {
        return !htmlStructure.contains("th:field=");
    }
}
