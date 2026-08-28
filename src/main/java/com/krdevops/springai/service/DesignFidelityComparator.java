package com.krdevops.springai.service;

import com.krdevops.springai.model.design.DesignFidelityReport;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 두 {@link UiDesignSpec}(Figma 원본 분석 결과 vs 생성된 화면을 재캡처해 분석한 결과)의
 * 구조적 유사도를 계산한다. 이 프로젝트에는 실제 픽셀 스크린샷 캡처 기능이 없어(코드 확인
 * 완료), 대신 Figma 경로와 웹 캡처 경로가 이미 같은 {@link UiDesignSpec} 타입을 만들어내는
 * 것을 이용해 archetype/컴포넌트 타입/필드 역할/액션 타입 집합의 Jaccard 유사도로 비교한다 —
 * Figma_픽셀재현_제외범위_구현계획.md 트랙 A.
 */
@Service
public class DesignFidelityComparator {

    public DesignFidelityReport compare(
            String originalAnalysisId, UiDesignSpec original,
            String renderedAnalysisId, UiDesignSpec rendered) {
        Set<String> originalComponents = componentTypes(original);
        Set<String> renderedComponents = componentTypes(rendered);
        Set<String> originalRoles = fieldRoles(original);
        Set<String> renderedRoles = fieldRoles(rendered);
        Set<String> originalActions = actionTypes(original);
        Set<String> renderedActions = actionTypes(rendered);

        Set<String> missing = new LinkedHashSet<>();
        missing.addAll(difference(originalComponents, renderedComponents, "component:"));
        missing.addAll(difference(originalRoles, renderedRoles, "fieldRole:"));
        missing.addAll(difference(originalActions, renderedActions, "action:"));

        Set<String> extra = new LinkedHashSet<>();
        extra.addAll(difference(renderedComponents, originalComponents, "component:"));
        extra.addAll(difference(renderedRoles, originalRoles, "fieldRole:"));
        extra.addAll(difference(renderedActions, originalActions, "action:"));

        return new DesignFidelityReport(
                originalAnalysisId, renderedAnalysisId,
                archetypeMatch(original, rendered),
                jaccard(originalComponents, renderedComponents),
                jaccard(originalRoles, renderedRoles),
                jaccard(originalActions, renderedActions),
                List.copyOf(missing), List.copyOf(extra));
    }

    private double archetypeMatch(UiDesignSpec original, UiDesignSpec rendered) {
        String left = original.archetype();
        String right = rendered.archetype();
        if (left == null || right == null) return left == right ? 1.0 : 0.0;
        return left.equalsIgnoreCase(right) ? 1.0 : 0.0;
    }

    private Set<String> componentTypes(UiDesignSpec spec) {
        Set<String> result = new LinkedHashSet<>();
        spec.components().forEach(component -> result.add(component.type()));
        return result;
    }

    private Set<String> fieldRoles(UiDesignSpec spec) {
        Set<String> result = new LinkedHashSet<>();
        spec.fieldHints().forEach(field -> result.add(field.role().name()));
        return result;
    }

    private Set<String> actionTypes(UiDesignSpec spec) {
        Set<String> result = new LinkedHashSet<>();
        spec.actions().forEach(action -> result.add(action.type()));
        return result;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private Set<String> difference(Set<String> left, Set<String> right, String prefix) {
        Set<String> result = new LinkedHashSet<>();
        for (String item : left) {
            if (!right.contains(item)) result.add(prefix + item);
        }
        return result;
    }
}
