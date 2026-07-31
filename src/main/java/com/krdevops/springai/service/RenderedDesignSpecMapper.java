package com.krdevops.springai.service;

import com.krdevops.springai.model.capture.SafeDesignProjection;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class RenderedDesignSpecMapper {

    /** 03번 §9.3: 인식 confidence가 이 임계값 미만이면 자동 확정하지 않고 uncertainties에 기록한다. */
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    public UiDesignSpec map(SafeDesignProjection projection, String featureType) {
        String archetype = archetype(projection, featureType);
        List<UiDesignSpec.ComponentSpec> components = projection.components().stream()
                .map(value -> new UiDesignSpec.ComponentSpec(value.type(), value.evidence()))
                .toList();
        List<UiDesignSpec.FieldHint> fields = projection.fields().stream()
                .map(value -> new UiDesignSpec.FieldHint(value.id(), value.label(), role(value.role()),
                        value.control(), value.confidence()))
                .toList();
        List<UiDesignSpec.ActionSpec> actions = projection.actions().stream()
                .map(value -> new UiDesignSpec.ActionSpec(actionType(value.label()), importance(value.label())))
                .toList();
        UiDesignSpec.LayoutSpec layout = new UiDesignSpec.LayoutSpec(
                "WEB", projection.documentWidth() > 1200 ? "WIDE" : "STANDARD",
                components.size() > 12 ? "COMPACT" : "COMFORTABLE",
                fields.size() > 6 ? "two-column" : "single-column", "top-right",
                components.stream().anyMatch(value -> contains(value.type(), "SEARCH_PANEL"))
                        ? "above-table" : "none");
        return new UiDesignSpec(archetype, layout, components, actions, fields,
                projection.tokens(), List.of(), uncertainties(projection));
    }

    private List<String> uncertainties(SafeDesignProjection projection) {
        List<String> result = new java.util.ArrayList<>(projection.warnings());
        for (var component : projection.components()) {
            if (component.confidence() < CONFIDENCE_THRESHOLD) {
                result.add("component candidate confidence 낮음: %s(%.2f)".formatted(component.type(), component.confidence()));
            }
        }
        for (var field : projection.fields()) {
            if (field.confidence() < CONFIDENCE_THRESHOLD) {
                result.add("field confidence 낮음: %s(%.2f)".formatted(field.id(), field.confidence()));
            }
        }
        return result;
    }

    private String archetype(SafeDesignProjection projection, String featureType) {
        boolean table = projection.components().stream().anyMatch(c -> contains(c.type(), "TABLE"));
        boolean form = projection.components().stream().anyMatch(c -> contains(c.type(), "FORM"));
        if (table) return "BOARD_LIST";
        if (form || !projection.fields().isEmpty()) return "CRUD_FORM";
        return "board".equalsIgnoreCase(featureType) ? "BOARD_DETAIL" : "CRUD_LIST";
    }

    private UiFieldRole role(String value) {
        try { return UiFieldRole.valueOf(value); } catch (Exception ignored) { return UiFieldRole.GENERIC; }
    }

    private String actionType(String label) {
        String value = label.toUpperCase(Locale.ROOT);
        if (value.contains("검색") || value.contains("SEARCH")) return "SEARCH";
        if (value.contains("등록") || value.contains("CREATE") || value.contains("NEW")) return "CREATE";
        if (value.contains("저장") || value.contains("SAVE")) return "SAVE";
        if (value.contains("수정") || value.contains("UPDATE")) return "UPDATE";
        if (value.contains("삭제") || value.contains("DELETE")) return "DELETE";
        if (value.contains("취소") || value.contains("CANCEL")) return "CANCEL";
        if (value.contains("뒤로") || value.contains("목록") || value.contains("BACK")) return "BACK";
        return "CUSTOM";
    }

    private String importance(String label) {
        String type = actionType(label);
        return SetHolder.PRIMARY.contains(type) ? "PRIMARY" : "SECONDARY";
    }

    private static boolean contains(String value, String token) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(token);
    }

    private static final class SetHolder {
        private static final java.util.Set<String> PRIMARY = java.util.Set.of("CREATE", "SAVE", "UPDATE", "SEARCH");
    }
}
