package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.ScreenHtmlSkeleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * I-4C: HTML Skeleton 생성 (구조 + 토큰, 필드 바인딩 없음).
 * Screen Structure + Component Inventory + Token → ScreenHtmlSkeleton
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenHtmlSkeletonGenerator {

    /**
     * HTML Skeleton 생성.
     *
     * @param spec ScreenSpecification
     * @param archetype LIST, FORM, DETAIL
     * @return ScreenHtmlSkeleton
     */
    public ScreenHtmlSkeleton generate(ScreenSpecification spec, String archetype) {
        if (spec == null || archetype == null) {
            return new ScreenHtmlSkeleton(
                UUID.randomUUID().toString(),
                "invalid",
                archetype,
                "",
                Map.of(),
                List.of(),
                List.of("spec 또는 archetype이 null"),
                LocalDateTime.now()
            );
        }

        // 1. 기본 CSS 변수 생성 (표준 디자인 토큰)
        var cssVariables = generateDefaultTokens();

        // 2. HTML 구조 생성
        var html = generateStructure(archetype);

        // 3. Slot 목록
        var slots = generateSlots(archetype);

        // 4. 검증
        var issues = new java.util.ArrayList<String>();
        if (!isValidArchetype(archetype)) {
            issues.add("알 수 없는 archetype: " + archetype);
        }

        return new ScreenHtmlSkeleton(
            UUID.randomUUID().toString(),
            spec.screenName(),
            archetype,
            html,
            cssVariables,
            slots,
            issues,
            LocalDateTime.now()
        );
    }

    private Map<String, String> generateDefaultTokens() {
        return Map.ofEntries(
            Map.entry("--color-primary", "#007bff"),
            Map.entry("--color-secondary", "#6c757d"),
            Map.entry("--color-success", "#28a745"),
            Map.entry("--color-danger", "#dc3545"),
            Map.entry("--spacing-base", "1rem"),
            Map.entry("--spacing-sm", "0.5rem"),
            Map.entry("--spacing-md", "1.5rem"),
            Map.entry("--spacing-lg", "2rem"),
            Map.entry("--font-size-base", "1rem"),
            Map.entry("--font-size-sm", "0.875rem"),
            Map.entry("--border-radius", "0.25rem")
        );
    }

    private String generateStructure(String archetype) {
        return switch (archetype.toUpperCase()) {
            case "LIST" -> """
                <div class="page-list">
                  <div class="search-panel"><!-- slot: search-fields --></div>
                  <table class="table">
                    <thead><tr><!-- slot: list-headers --></tr></thead>
                    <tbody><!-- slot: list-rows --></tbody>
                  </table>
                  <div class="actions"><!-- slot: actions --></div>
                </div>
                """;
            case "FORM" -> """
                <div class="page-form">
                  <form class="form-container">
                    <div class="form-fields"><!-- slot: form-fields --></div>
                    <div class="form-actions"><!-- slot: actions --></div>
                  </form>
                </div>
                """;
            case "DETAIL" -> """
                <div class="page-detail">
                  <div class="detail-header"><!-- slot: header --></div>
                  <dl class="detail-list"><!-- slot: detail-fields --></dl>
                  <div class="detail-actions"><!-- slot: actions --></div>
                </div>
                """;
            default -> "";
        };
    }

    private List<String> generateSlots(String archetype) {
        var slots = new java.util.ArrayList<String>();
        slots.add("actions");

        return switch (archetype.toUpperCase()) {
            case "LIST" -> {
                slots.addAll(0, List.of("search-fields", "list-headers", "list-rows"));
                yield slots;
            }
            case "FORM" -> {
                slots.add(0, "form-fields");
                yield slots;
            }
            case "DETAIL" -> {
                slots.addAll(0, List.of("header", "detail-fields"));
                yield slots;
            }
            default -> slots;
        };
    }

    private boolean isValidArchetype(String archetype) {
        return archetype != null && (
            archetype.equalsIgnoreCase("LIST")
            || archetype.equalsIgnoreCase("FORM")
            || archetype.equalsIgnoreCase("DETAIL")
        );
    }
}
