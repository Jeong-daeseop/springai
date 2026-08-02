package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * I-4A: Layout 판정 (layoutDensity, formColumnLayout, actionPlacement, searchPanel).
 * 화면 구조와 필드 수 기반으로 레이아웃 정책을 결정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LayoutTypeResolver {

    /**
     * layoutDensity 판정: STANDARD, COMPACT, COMFORTABLE
     *
     * @param spec ScreenSpecification
     * @return LayoutDensityDecision
     */
    public LayoutDensityDecision resolveLayoutDensity(ScreenSpecification spec) {
        if (spec == null) {
            return new LayoutDensityDecision(
                LayoutDensity.STANDARD,
                0.8,
                "기본값"
            );
        }

        // 1. ScreenSpecification에 이미 layoutDensity가 설정된 경우
        if (spec.layoutDensity() != null && spec.layoutDensity() != LayoutDensity.STANDARD) {
            return new LayoutDensityDecision(
                spec.layoutDensity(),
                0.95,
                "ScreenSpecification에서 명시됨: " + spec.layoutDensity()
            );
        }

        // 2. 필드 수 기반 판정
        var totalFields = spec.pages().stream()
            .mapToInt(p -> p.fields() != null ? p.fields().size() : 0)
            .sum();

        if (totalFields > 15) {
            return new LayoutDensityDecision(
                LayoutDensity.COMPACT,
                0.8,
                "필드 수(" + totalFields + ") > 15 → COMPACT (공간 절약)"
            );
        }

        if (totalFields <= 5) {
            return new LayoutDensityDecision(
                LayoutDensity.COMFORTABLE,
                0.7,
                "필드 수(" + totalFields + ") ≤ 5 → COMFORTABLE (여유)"
            );
        }

        // 기본값
        return new LayoutDensityDecision(
            LayoutDensity.STANDARD,
            0.8,
            "필드 수(" + totalFields + ") 6~15 → STANDARD (균형)"
        );
    }

    /**
     * formColumnLayout 판정: SINGLE_COLUMN, TWO_COLUMN, THREE_COLUMN
     *
     * @param spec ScreenSpecification
     * @return FormColumnLayoutDecision
     */
    public FormColumnLayoutDecision resolveFormColumnLayout(ScreenSpecification spec) {
        if (spec == null || spec.pages() == null || spec.pages().isEmpty()) {
            return new FormColumnLayoutDecision(
                FormColumnLayout.SINGLE_COLUMN,
                0.8,
                "기본값"
            );
        }

        // 1. ScreenSpecification에 이미 설정된 경우
        if (spec.formColumnLayout() != null && spec.formColumnLayout() != FormColumnLayout.SINGLE_COLUMN) {
            return new FormColumnLayoutDecision(
                spec.formColumnLayout(),
                0.95,
                "ScreenSpecification에서 명시됨: " + spec.formColumnLayout()
            );
        }

        // 2. 필드 수 기반 판정
        var totalFields = spec.pages().stream()
            .mapToInt(p -> p.fields() != null ? p.fields().size() : 0)
            .sum();

        if (totalFields > 6) {
            return new FormColumnLayoutDecision(
                FormColumnLayout.TWO_COLUMN,
                0.75,
                "필드 수(" + totalFields + ") > 6 → TWO_COLUMN"
            );
        }

        // 기본값
        return new FormColumnLayoutDecision(
            FormColumnLayout.SINGLE_COLUMN,
            0.8,
            "필드 수(" + totalFields + ") ≤ 6 → SINGLE_COLUMN"
        );
    }

    /**
     * actionPlacement 판정: TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, INLINE
     *
     * @param archetype LIST, FORM, DETAIL 등
     * @return ActionPlacementDecision
     */
    public ActionPlacementDecision resolveActionPlacement(String archetype) {
        if (archetype == null) {
            return new ActionPlacementDecision(
                ActionPlacement.TOP_RIGHT,
                0.8,
                "기본값"
            );
        }

        return switch (archetype.toUpperCase()) {
            case "LIST" -> new ActionPlacementDecision(
                ActionPlacement.TOP_RIGHT,
                0.9,
                "LIST: 조회/추가/삭제 액션은 우측 상단에 배치"
            );
            case "FORM" -> new ActionPlacementDecision(
                ActionPlacement.BOTTOM_RIGHT,
                0.9,
                "FORM: 저장/취소 버튼은 하단에 배치"
            );
            case "DETAIL" -> new ActionPlacementDecision(
                ActionPlacement.TOP_RIGHT,
                0.85,
                "DETAIL: 편집/삭제는 우측 상단, 목록이동은 하단"
            );
            default -> new ActionPlacementDecision(
                ActionPlacement.TOP_RIGHT,
                0.7,
                "기본값: TOP_RIGHT"
            );
        };
    }

    /**
     * searchPanelPlacement 판정
     *
     * @param archetype LIST, FORM, DETAIL 등
     * @return SearchPanelPlacementDecision
     */
    public SearchPanelPlacementDecision resolveSearchPanelPlacement(String archetype) {
        if (archetype == null || !archetype.equalsIgnoreCase("LIST")) {
            return new SearchPanelPlacementDecision(
                SearchPanelPlacement.NONE,
                0.9,
                "검색창은 LIST 화면에만 배치"
            );
        }

        return new SearchPanelPlacementDecision(
            SearchPanelPlacement.ABOVE_TABLE,
            0.95,
            "LIST: 테이블 상단에 검색 조건 패널 배치"
        );
    }

    public record LayoutDensityDecision(
            LayoutDensity density,
            double confidence,
            String reasoning
    ) {}

    public record FormColumnLayoutDecision(
            FormColumnLayout columnLayout,
            double confidence,
            String reasoning
    ) {}

    public record ActionPlacementDecision(
            ActionPlacement placement,
            double confidence,
            String reasoning
    ) {}

    public record SearchPanelPlacementDecision(
            SearchPanelPlacement placement,
            double confidence,
            String reasoning
    ) {}
}