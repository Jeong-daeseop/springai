package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Screen Pattern의 cardinality뿐 아니라 Role tree와 Slot 순서를 함께 검증한다. */
@Component
public class ScreenPatternValidator {

    public List<DesignSystemIssue> validate(
            ScreenPatternDefinition definition,
            FigmaNodeSpec semanticRoot
    ) {
        if (definition == null) {
            return List.of(issue("SCREEN_PATTERN_NOT_RESOLVED", DesignSystemIssue.Severity.FATAL,
                    "Screen Pattern 정의가 없습니다.", null));
        }
        if (semanticRoot == null) {
            return List.of(issue("PATTERN_ROOT_MISSING", DesignSystemIssue.Severity.FATAL,
                    "검증할 Semantic Node Tree가 없습니다.", null));
        }

        List<DesignSystemIssue> issues = new ArrayList<>();
        List<RoleOccurrence> occurrences = new ArrayList<>();
        collect(semanticRoot, null, occurrences, issues);

        Map<SemanticRole, ScreenPatternDefinition.SlotDefinition> slots = new EnumMap<>(SemanticRole.class);
        Set<SemanticRole> allowedRoles = EnumSet.noneOf(SemanticRole.class);
        for (ScreenPatternDefinition.SlotDefinition slot : definition.slots()) {
            slots.put(slot.role(), slot);
            allowedRoles.add(slot.role());
            allowedRoles.addAll(slot.allowedChildren());
        }

        Map<SemanticRole, Integer> counts = new EnumMap<>(SemanticRole.class);
        occurrences.forEach(occurrence -> counts.merge(occurrence.role(), 1, Integer::sum));
        for (ScreenPatternDefinition.SlotDefinition slot : definition.slots()) {
            int count = counts.getOrDefault(slot.role(), 0);
            if (count < slot.minCount()) {
                issues.add(issue("PATTERN_REQUIRED_SLOT_MISSING", DesignSystemIssue.Severity.ERROR,
                        slot.role().code() + "의 최소 개수 " + slot.minCount() + "를 충족하지 못했습니다.",
                        slot.role().code()));
            }
            if (slot.maxCount() != null && count > slot.maxCount()) {
                issues.add(issue("PATTERN_SLOT_CARDINALITY_VIOLATION", DesignSystemIssue.Severity.ERROR,
                        slot.role().code() + "의 최대 개수 " + slot.maxCount() + "를 초과했습니다.",
                        slot.role().code()));
            }
        }

        for (RoleOccurrence occurrence : occurrences) {
            if (!allowedRoles.contains(occurrence.role())) {
                issues.add(issue("PATTERN_ROLE_NOT_ALLOWED", DesignSystemIssue.Severity.ERROR,
                        definition.pattern().code() + "에서 허용되지 않은 Role입니다: " + occurrence.role().code(),
                        occurrence.logicalNodeId()));
            }
            if (occurrence.parentRole() != null) {
                ScreenPatternDefinition.SlotDefinition parentSlot = slots.get(occurrence.parentRole());
                if (parentSlot == null || !parentSlot.allowedChildren().contains(occurrence.role())) {
                    issues.add(issue("PATTERN_CHILD_ROLE_NOT_ALLOWED", DesignSystemIssue.Severity.ERROR,
                            occurrence.parentRole().code() + " 아래에 " + occurrence.role().code()
                                    + " Role을 배치할 수 없습니다.", occurrence.logicalNodeId()));
                }
            }
        }

        validateFieldModes(definition.pattern(), occurrences, issues);
        validateRequiredActions(definition.pattern(), counts, issues);

        int lastOrder = Integer.MIN_VALUE;
        SemanticRole lastRole = null;
        for (RoleOccurrence occurrence : occurrences) {
            ScreenPatternDefinition.SlotDefinition slot = slots.get(occurrence.role());
            if (slot == null) continue;
            if (slot.order() < lastOrder) {
                issues.add(issue("PATTERN_SLOT_ORDER_VIOLATION", DesignSystemIssue.Severity.ERROR,
                        occurrence.role().code() + " Slot이 " + lastRole.code() + " 뒤에 올 수 없습니다.",
                        occurrence.logicalNodeId()));
            } else {
                lastOrder = slot.order();
                lastRole = occurrence.role();
            }
        }
        return List.copyOf(issues);
    }

    private void validateFieldModes(
            ScreenPattern pattern,
            List<RoleOccurrence> occurrences,
            List<DesignSystemIssue> issues
    ) {
        List<RoleOccurrence> fields = occurrences.stream()
                .filter(occurrence -> occurrence.role().code().startsWith("field."))
                .toList();
        boolean editableFound = false;
        for (RoleOccurrence field : fields) {
            Object rawMode = field.properties().get("mode");
            FieldMode mode;
            try {
                mode = rawMode instanceof String value ? FieldMode.valueOf(value) : null;
            } catch (IllegalArgumentException exception) {
                mode = null;
            }
            if (mode == null) {
                issues.add(issue("PATTERN_FIELD_MODE_MISSING", DesignSystemIssue.Severity.ERROR,
                        "Field Role에는 유효한 mode가 필요합니다.", field.logicalNodeId()));
                continue;
            }
            editableFound |= mode == FieldMode.EDITABLE;
            if (pattern == ScreenPattern.CRUD_DETAIL && mode != FieldMode.READ_ONLY) {
                issues.add(issue("PATTERN_FIELD_MODE_VIOLATION", DesignSystemIssue.Severity.ERROR,
                        "DETAIL 화면의 Field는 READ_ONLY여야 합니다.", field.logicalNodeId()));
            }
        }
        if ((pattern == ScreenPattern.CRUD_CREATE || pattern == ScreenPattern.CRUD_EDIT)
                && !fields.isEmpty() && !editableFound) {
            issues.add(issue("PATTERN_EDITABLE_FIELD_MISSING", DesignSystemIssue.Severity.ERROR,
                    pattern.code() + " 화면에는 EDITABLE Field가 하나 이상 필요합니다.", null));
        }
    }

    private void validateRequiredActions(
            ScreenPattern pattern,
            Map<SemanticRole, Integer> counts,
            List<DesignSystemIssue> issues
    ) {
        if ((pattern == ScreenPattern.CRUD_CREATE || pattern == ScreenPattern.CRUD_EDIT)
                && counts.getOrDefault(SemanticRole.ACTION_PRIMARY, 0) == 0) {
            issues.add(issue("PATTERN_PRIMARY_ACTION_MISSING", DesignSystemIssue.Severity.ERROR,
                    pattern.code() + " 화면에 저장 역할의 Primary Action이 필요합니다.", null));
        }
    }

    private void collect(
            FigmaNodeSpec node,
            SemanticRole parentRole,
            List<RoleOccurrence> occurrences,
            List<DesignSystemIssue> issues
    ) {
        SemanticRole effectiveParent = parentRole;
        Object rawRole = node.properties().get("semanticRole");
        if (rawRole instanceof String roleCode) {
            try {
                SemanticRole role = SemanticRole.fromCode(roleCode);
                occurrences.add(new RoleOccurrence(role, parentRole, node.logicalNodeId(), node.properties()));
                effectiveParent = role;
            } catch (IllegalArgumentException exception) {
                issues.add(issue("PATTERN_UNKNOWN_ROLE", DesignSystemIssue.Severity.ERROR,
                        "표준 SemanticRole에 등록되지 않은 Role입니다: " + roleCode, node.logicalNodeId()));
            }
        }
        for (FigmaNodeSpec child : node.children()) {
            collect(child, effectiveParent, occurrences, issues);
        }
    }

    private DesignSystemIssue issue(String code, DesignSystemIssue.Severity severity, String message, String target) {
        return new DesignSystemIssue(code, severity, message, target);
    }

    private record RoleOccurrence(
            SemanticRole role,
            SemanticRole parentRole,
            String logicalNodeId,
            Map<String, Object> properties
    ) {}
}
