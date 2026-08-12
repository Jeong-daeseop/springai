package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * KRV-072: Registry·Rule Set 버전 사이의 Breaking Change(Role 제거, Axis 제거, Property 이름 변경,
 * Rule 결과 변경)를 감지한다. {@link ComponentRegistrySyncService}의 {@code detectComponentDrift}는
 * Property 존재 여부·Type 변경만 다루므로, 이 클래스는 그보다 상위 개념인 Role/Axis/Rule 계약
 * 변경을 별도로 감지한다.
 *
 * <p>영향받는 최신 화면 목록은 이 클래스가 직접 조회하지 않는다. 같은 profileId/registryVersion에
 * 바인딩된 화면 전체는 이미 {@code FigmaOperationsService.impact(profileId, profileVersion, registryVersion)}
 * (REST {@code GET /api/figma/operations/design-system-impact/{profileId}})가
 * {@code FigmaScreenSpecRepository.findLatestByDesignSystem(...)}로 제공하므로, 호출자는 이 클래스의
 * {@link BreakingChange} 목록과 그 impact 조회 결과를 함께 보고 검토하면 된다.</p>
 */
@Component
public class ComponentRegistryBreakingChangeAnalyzer {

    public List<BreakingChange> analyzeRegistry(ComponentRegistry previous, ComponentRegistry candidate) {
        List<BreakingChange> changes = new ArrayList<>();
        if (previous == null || candidate == null) {
            return changes;
        }
        previous.components().forEach((logicalType, before) -> {
            ComponentRegistryEntry after = candidate.components().get(logicalType);
            if (after == null) {
                return;
            }
            for (SemanticRole role : before.roles()) {
                if (!after.roles().contains(role)) {
                    changes.add(new BreakingChange(logicalType, ChangeType.ROLE_REMOVED, role.code()));
                }
            }
            for (String axisName : before.variantAxes().keySet()) {
                if (!after.variantAxes().containsKey(axisName)) {
                    changes.add(new BreakingChange(logicalType, ChangeType.AXIS_REMOVED, axisName));
                }
            }
            before.properties().forEach((logicalName, beforeMapping) -> {
                ComponentRegistryEntry.PropertyMapping afterMapping = after.properties().get(logicalName);
                if (afterMapping != null
                        && !Objects.equals(beforeMapping.figmaProperty(), afterMapping.figmaProperty())) {
                    changes.add(new BreakingChange(logicalType, ChangeType.PROPERTY_RENAMED,
                            logicalName + ": " + beforeMapping.figmaProperty() + " -> " + afterMapping.figmaProperty()));
                }
            });
        });
        return List.copyOf(changes);
    }

    public List<BreakingChange> analyzeRuleSet(VariantRuleSet previous, VariantRuleSet candidate) {
        List<BreakingChange> changes = new ArrayList<>();
        if (previous == null || candidate == null) {
            return changes;
        }
        Map<String, VariantRule> candidateRules = new LinkedHashMap<>();
        candidate.rules().forEach(rule -> candidateRules.put(rule.ruleId(), rule));
        for (VariantRule before : previous.rules()) {
            VariantRule after = candidateRules.get(before.ruleId());
            if (after == null) {
                changes.add(new BreakingChange(before.ruleId(), ChangeType.RULE_REMOVED, before.role().code()));
                continue;
            }
            if (!before.result().equals(after.result())) {
                changes.add(new BreakingChange(before.ruleId(), ChangeType.RULE_RESULT_CHANGED,
                        describe(before.result()) + " -> " + describe(after.result())));
            }
            if (!before.when().equals(after.when())) {
                changes.add(new BreakingChange(before.ruleId(), ChangeType.RULE_CONDITION_CHANGED,
                        describe(before.when()) + " -> " + describe(after.when())));
            }
        }
        return List.copyOf(changes);
    }

    private String describe(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    public enum ChangeType { ROLE_REMOVED, AXIS_REMOVED, PROPERTY_RENAMED, RULE_REMOVED, RULE_RESULT_CHANGED, RULE_CONDITION_CHANGED }

    public record BreakingChange(String subjectId, ChangeType type, String detail) {}
}
