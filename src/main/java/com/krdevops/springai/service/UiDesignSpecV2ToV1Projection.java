package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.policy.UiFieldRolePolicy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 기존 ScreenSpecAssembler가 소비할 수 있도록 v2의 명시적 시각 후보만 v1 View로 투영한다. */
@Component
public class UiDesignSpecV2ToV1Projection {

    public UiDesignSpec project(UiDesignSpecV2 spec, String featureType) {
        List<UiDesignSpec.ComponentSpec> components = spec.nodes().stream()
                .filter(node -> node.logicalType() != null)
                .map(node -> new UiDesignSpec.ComponentSpec(
                        node.logicalType(), node.evidence().sourceNodeRefs()))
                .toList();
        List<UiDesignSpec.FieldHint> fields = spec.nodes().stream()
                .filter(node -> node.role().toLowerCase(Locale.ROOT).contains("field-candidate"))
                .map(node -> field(node))
                .toList();
        // Action은 Controller Route·HTTP Method·Permission과 결합된 업무 계약이다.
        // v2의 시각 Action 후보는 Design IR에만 남기고 기존 업무 Action 입력으로 투영하지 않는다.
        List<UiDesignSpec.ActionSpec> actions = List.of();
        List<String> uncertainties = spec.issues().stream()
                .filter(issue -> issue.severity() != UiDesignSpecV2.Severity.INFO)
                .map(UiDesignSpecV2.DesignIssue::message)
                .toList();
        List<UiDesignSpec.NodeGeometry> geometry = spec.nodes().stream()
                .filter(node -> node.geometry() != null)
                .map(this::geometry)
                .toList();
        return new UiDesignSpec(
                archetype(featureType), null, components, actions, fields,
                Map.of(), List.of(), uncertainties, geometry);
    }

    private UiDesignSpec.NodeGeometry geometry(UiDesignSpecV2.SemanticNode node) {
        UiDesignSpecV2.Geometry source = node.geometry();
        UiDesignSpecV2.VisualStyle style = node.visualStyle();
        String fill = style == null || style.fills().isEmpty() ? null : style.fills().get(0).color();
        String stroke = style == null || style.strokes().isEmpty() ? null : style.strokes().get(0).color();
        Double opacity = style == null ? null : style.opacity();
        List<UiDesignSpec.PaintSpec> fills = style == null ? List.of() : style.fills().stream()
                .map(paint -> new UiDesignSpec.PaintSpec(paint.type(), paint.visible(), paint.opacity(),
                        paint.color())).toList();
        List<UiDesignSpec.PaintSpec> strokes = style == null ? List.of() : style.strokes().stream()
                .map(paint -> new UiDesignSpec.PaintSpec(paint.type(), paint.visible(), paint.opacity(),
                        paint.color())).toList();
        return new UiDesignSpec.NodeGeometry(node.semanticId(), node.logicalType(), node.semanticId(),
                source.x(), source.y(), source.width(), source.height(), null, opacity,
                fill, stroke, null, null, List.of(), fills, strokes);
    }

    private UiDesignSpec.FieldHint field(UiDesignSpecV2.SemanticNode node) {
        String id = candidateName(node.semanticId());
        UiFieldRole role = UiFieldRolePolicy.inferRole(id);
        return new UiDesignSpec.FieldHint(
                id, id, role, control(role), node.evidence().confidence());
    }

    private String candidateName(String semanticId) {
        return semanticId.replaceFirst("^(field|action|node)-", "")
                .replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String archetype(String featureType) {
        return featureType != null && featureType.toLowerCase(Locale.ROOT).contains("board")
                ? "BOARD_LIST" : "CRUD_LIST";
    }

    private String control(UiFieldRole role) {
        return switch (role) {
            case CONTENT -> "TEXTAREA";
            case CATEGORY, STATUS -> "SELECT";
            case CREATED_AT, UPDATED_AT -> "DATE";
            case ATTACHMENT -> "FILE";
            default -> "TEXT";
        };
    }
}
