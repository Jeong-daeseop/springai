package com.krdevops.springai.model.thymeleaf;

import java.util.List;
import java.util.Map;

/** JSP 파일 1개를 정적으로 읽어 추출한 전체 증거(I-2C). */
public record JspEvidence(
        String jspPath,
        Map<String, String> taglibPrefixes,
        List<String> includes,
        List<JspFormEvidence> forms,
        List<JspModelReferenceEvidence> modelReferences,
        List<JspForEachBindingEvidence> forEachBindings,
        List<JspDisplayFieldEvidence> displayFields,
        List<String> routeReferences
) {
    public JspEvidence {
        if (jspPath == null || jspPath.isBlank()) {
            throw new IllegalArgumentException("jspPath는 필수입니다.");
        }
        taglibPrefixes = taglibPrefixes == null ? Map.of() : Map.copyOf(taglibPrefixes);
        includes = includes == null ? List.of() : List.copyOf(includes);
        forms = forms == null ? List.of() : List.copyOf(forms);
        modelReferences = modelReferences == null ? List.of() : List.copyOf(modelReferences);
        forEachBindings = forEachBindings == null ? List.of() : List.copyOf(forEachBindings);
        displayFields = displayFields == null ? List.of() : List.copyOf(displayFields);
        routeReferences = routeReferences == null ? List.of() : List.copyOf(routeReferences);
    }
}
