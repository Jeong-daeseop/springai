package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** R7-002: .figpack/Figma document 변환 결과의 결정론적 품질 Gate. */
@Service
public class FigmaUiDesignSpecQualityEvaluator {
    public Evaluation evaluate(UiDesignSpec spec) {
        if (spec == null) return new Evaluation(0.0, List.of("UI_SPEC_NULL"));
        List<String> issues = new ArrayList<>();
        int checks = 5;
        int passed = 0;
        if (spec.archetype() != null && !spec.archetype().isBlank()) passed++; else issues.add("ARCHETYPE_MISSING");
        if (spec.layout() != null) passed++; else issues.add("LAYOUT_MISSING");
        if (!spec.components().isEmpty()) passed++; else issues.add("COMPONENTS_EMPTY");
        if (!spec.fieldHints().isEmpty()) passed++; else issues.add("FIELD_HINTS_EMPTY");
        if (!spec.uncertainties().isEmpty() || !spec.interactions().isEmpty()) passed++; else issues.add("EVIDENCE_MISSING");
        return new Evaluation((double) passed / checks, List.copyOf(issues));
    }

    public record Evaluation(double score, List<String> issues) {
        public boolean passed() { return score >= 0.6; }
    }
}
