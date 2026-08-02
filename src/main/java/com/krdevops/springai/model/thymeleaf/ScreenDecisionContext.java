package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.SearchPanelPlacement;

import java.util.List;
import java.util.Map;

/**
 * I-4A: 화면·Component 결정 결과를 담는 컨텍스트 모델.
 * 화면 유형·Layout·Component 선택 이유와 신뢰도를 기록한다.
 */
public record ScreenDecisionContext(
        String screenName,
        String selectedArchetype,      // LIST, FORM, DETAIL, DASHBOARD
        double archetypeConfidence,    // 0.0 ~ 1.0
        String archetypeReasoning,

        String selectedFeatureType,    // CRUD, BOARD, MASTER_DETAIL, DASHBOARD
        double featureTypeConfidence,
        String featureTypeReasoning,

        LayoutDensity selectedLayoutDensity,
        double layoutDensityConfidence,
        String layoutDensityReasoning,

        FormColumnLayout selectedFormColumnLayout,
        double formColumnLayoutConfidence,
        String formColumnLayoutReasoning,

        ActionPlacement selectedActionPlacement,
        SearchPanelPlacement selectedSearchPanelPlacement,

        List<String> selectedComponentKeys,
        List<String> fallbackComponents,
        Map<String, Double> componentConfidence,

        List<String> issues,
        boolean readyForGeneration
) {
    public ScreenDecisionContext {
        selectedComponentKeys = selectedComponentKeys == null ? List.of() : List.copyOf(selectedComponentKeys);
        fallbackComponents = fallbackComponents == null ? List.of() : List.copyOf(fallbackComponents);
        componentConfidence = componentConfidence == null ? Map.of() : Map.copyOf(componentConfidence);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean isHighConfidence() {
        return archetypeConfidence >= 0.8
            && featureTypeConfidence >= 0.8
            && layoutDensityConfidence >= 0.7;
    }
}