package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I-6B: Thymeleaf 화면 구조 → Figma 컴포넌트 매핑.
 */
@Service
public class ScreenToFigmaComponentMapper {

    private static final Pattern TH_COMPONENT_PATTERN = Pattern.compile("th:object=\"\\*\\{([^}]+)\\}\"");
    private static final Pattern FORM_FIELD_PATTERN = Pattern.compile("th:field=\"\\*\\{([^}]+)\\}\"");

    /**
     * Thymeleaf HTML 구조를 분석하여 Figma 컴포넌트 맵을 생성합니다.
     */
    public Map<String, Object> mapHtmlToFigmaComponents(String htmlContent, String screenName) {
        Map<String, Object> componentMap = new HashMap<>();
        componentMap.put("screenName", screenName);
        componentMap.put("components", extractComponents(htmlContent));
        componentMap.put("fields", extractFormFields(htmlContent));
        componentMap.put("timestamp", System.currentTimeMillis());

        return componentMap;
    }

    /**
     * HTML에서 Thymeleaf 객체 바인딩을 추출합니다.
     */
    public List<String> extractComponents(String htmlContent) {
        List<String> components = new ArrayList<>();
        Matcher matcher = TH_COMPONENT_PATTERN.matcher(htmlContent);

        while (matcher.find()) {
            components.add(matcher.group(1));
        }

        return components;
    }

    /**
     * HTML에서 폼 필드 바인딩을 추출합니다.
     */
    public List<String> extractFormFields(String htmlContent) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = FORM_FIELD_PATTERN.matcher(htmlContent);

        while (matcher.find()) {
            fields.add(matcher.group(1));
        }

        return fields;
    }

    /**
     * 컴포넌트를 Figma node 구조로 변환합니다.
     */
    public Map<String, Object> convertToFigmaNodeStructure(List<String> components, List<String> fields) {
        Map<String, Object> structure = new HashMap<>();
        structure.put("type", "FRAME");
        structure.put("name", "Thymeleaf Components");
        structure.put("children", buildChildrenNodes(components, fields));

        return structure;
    }

    private List<Map<String, Object>> buildChildrenNodes(List<String> components, List<String> fields) {
        List<Map<String, Object>> children = new ArrayList<>();

        for (String component : components) {
            Map<String, Object> node = new HashMap<>();
            node.put("type", "COMPONENT");
            node.put("name", component);
            node.put("binding", "th:object=\"*{" + component + "}\"");
            children.add(node);
        }

        for (String field : fields) {
            Map<String, Object> fieldNode = new HashMap<>();
            fieldNode.put("type", "FIELD");
            fieldNode.put("name", field);
            fieldNode.put("binding", "th:field=\"*{" + field + "}\"");
            children.add(fieldNode);
        }

        return children;
    }
}
