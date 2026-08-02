package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.JspDisplayFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspEvidence;
import com.krdevops.springai.model.thymeleaf.JspForEachBindingEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormEvidence;
import com.krdevops.springai.model.thymeleaf.JspFormFieldEvidence;
import com.krdevops.springai.model.thymeleaf.JspModelReferenceEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * I-2C: eGovFrame JSP 소스를 정적으로 읽어 taglib·include·form·EL·forEach·표시 필드 증거를 추출한다.
 * JSP는 스크립틀릿이 섞인 비정형 마크업이라 AST 파서 대신, 이 저장소의 다른 Source Reader와 같은
 * 정규식 기반 line-level 추출 방식을 그대로 따른다.
 */
@Component
public class JspSourceReader {

    /**
     * 여는 태그의 속성 구간. {@code [^>]*}만 쓰면 {@code action="<c:url .../>"}처럼 속성값 안에
     * '>'가 포함된 경우(중첩 self-closing 태그) 태그가 끝나기 전에 잘려버린다. 따옴표로 묶인
     * 구간은 통째로 건너뛰어 그 안의 '>'를 태그 종료로 오인하지 않도록 한다.
     */
    private static final String TAG_ATTRS = "(?:[^\">]|\"[^\"]*\")*";

    private static final Pattern TAGLIB = Pattern.compile(
            "<%@\\s*taglib([^%]*)%>", Pattern.DOTALL);
    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile(
            "<%@\\s*include\\s+file=\"([^\"]+)\"\\s*%>");
    private static final Pattern INCLUDE_ACTION = Pattern.compile(
            "<jsp:include\\s+page=\"([^\"]+)\"");
    private static final Pattern FOR_EACH_OPEN = Pattern.compile(
            "<c:forEach\\b(" + TAG_ATTRS + ")>");
    private static final Pattern SPRING_FORM_BLOCK = Pattern.compile(
            "<form:form\\b(" + TAG_ATTRS + ")>(.*?)</form:form>", Pattern.DOTALL);
    private static final Pattern PLAIN_FORM_BLOCK = Pattern.compile(
            "<form(?!:)\\b(" + TAG_ATTRS + ")>(.*?)</form>", Pattern.DOTALL);
    private static final Pattern SPRING_FORM_FIELD = Pattern.compile(
            "<form:(input|select|textarea|checkbox|checkboxes|radiobutton|radiobuttons|password)\\b("
                    + TAG_ATTRS + ")/?>");
    private static final Pattern SPRING_FORM_ERRORS = Pattern.compile(
            "<form:errors\\b(" + TAG_ATTRS + ")/?>");
    private static final Pattern PLAIN_INPUT = Pattern.compile("<input\\b(" + TAG_ATTRS + ")/?>");
    private static final Pattern PLAIN_SELECT = Pattern.compile("<select\\b(" + TAG_ATTRS + ")>");
    private static final Pattern PLAIN_TEXTAREA = Pattern.compile("<textarea\\b(" + TAG_ATTRS + ")>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z:][\\w:-]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern C_URL_VALUE = Pattern.compile("<c:url\\s+value=['\"]([^'\"]+)['\"]");
    private static final Pattern EL_EXPRESSION = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern EL_PATH_EXPRESSION = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)(?:\\.[A-Za-z0-9_]+|\\[[^\\]]*\\])*");
    private static final Pattern TWO_SEGMENT_EL = Pattern.compile(
            "\\$\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");

    private static final Set<String> EL_RESERVED_WORDS = Set.of(
            "empty", "not", "and", "or", "eq", "ne", "lt", "gt", "le", "ge",
            "true", "false", "null", "div", "mod", "instanceof");
    private static final Set<String> JSP_IMPLICIT_OBJECTS = Set.of(
            "pageContext", "param", "paramValues", "header", "headerValues",
            "cookie", "initParam", "requestScope", "sessionScope", "applicationScope", "pageScope");

    public JspEvidence read(String jspPath, String content) {
        if (jspPath == null || jspPath.isBlank()) {
            throw new IllegalArgumentException("jspPath는 필수입니다.");
        }
        if (content == null) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }

        Map<String, String> taglibs = readTaglibs(content);
        List<String> includes = readIncludes(content);
        List<JspForEachBindingEvidence> forEachBindings = readForEachBindings(content, jspPath);
        Set<String> loopScopedNames = loopScopedNames(content);
        List<JspFormEvidence> forms = readForms(content, jspPath);
        List<JspModelReferenceEvidence> modelReferences =
                readModelReferences(content, loopScopedNames, jspPath);
        List<JspDisplayFieldEvidence> displayFields = readDisplayFields(content, jspPath);
        List<String> routeReferences = readRouteReferences(content);

        return new JspEvidence(
                jspPath, taglibs, includes, forms, modelReferences, forEachBindings, displayFields,
                routeReferences);
    }

    private Map<String, String> readTaglibs(String content) {
        Map<String, String> taglibs = new LinkedHashMap<>();
        Matcher matcher = TAGLIB.matcher(content);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String prefix = attributes.get("prefix");
            String uri = attributes.get("uri");
            if (prefix != null && uri != null) {
                taglibs.put(prefix, uri);
            }
        }
        return taglibs;
    }

    private List<String> readIncludes(String content) {
        List<String> includes = new ArrayList<>();
        Matcher directive = INCLUDE_DIRECTIVE.matcher(content);
        while (directive.find()) {
            includes.add(directive.group(1));
        }
        Matcher action = INCLUDE_ACTION.matcher(content);
        while (action.find()) {
            includes.add(action.group(1));
        }
        return includes;
    }

    private List<JspForEachBindingEvidence> readForEachBindings(String content, String jspPath) {
        List<JspForEachBindingEvidence> bindings = new ArrayList<>();
        Matcher matcher = FOR_EACH_OPEN.matcher(content);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String var = attributes.get("var");
            String items = attributes.get("items");
            if (var == null || items == null) {
                continue;
            }
            String itemsRoot = elRoot(items);
            if (itemsRoot != null) {
                bindings.add(new JspForEachBindingEvidence(var, itemsRoot, sourceLocation(jspPath, content, matcher.start())));
            }
        }
        return bindings;
    }

    private Set<String> loopScopedNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = FOR_EACH_OPEN.matcher(content);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            if (attributes.get("var") != null) {
                names.add(attributes.get("var"));
            }
            if (attributes.get("varStatus") != null) {
                names.add(attributes.get("varStatus"));
            }
        }
        return names;
    }

    private List<JspFormEvidence> readForms(String content, String jspPath) {
        List<JspFormEvidence> forms = new ArrayList<>();
        Matcher springForm = SPRING_FORM_BLOCK.matcher(content);
        while (springForm.find()) {
            forms.add(toSpringFormEvidence(
                    attributes(springForm.group(1)), springForm.group(2), jspPath, springForm.start()));
        }
        Matcher plainForm = PLAIN_FORM_BLOCK.matcher(content);
        while (plainForm.find()) {
            forms.add(toPlainFormEvidence(
                    attributes(plainForm.group(1)), plainForm.group(2), jspPath, plainForm.start()));
        }
        return forms;
    }

    private JspFormEvidence toSpringFormEvidence(
            Map<String, String> formAttributes, String body, String jspPath, int offset) {
        Map<String, JspFormFieldEvidence> fields = new LinkedHashMap<>();
        Matcher field = SPRING_FORM_FIELD.matcher(body);
        while (field.find()) {
            Map<String, String> attributes = attributes(field.group(2));
            String path = attributes.get("path");
            if (path == null) {
                continue;
            }
            fields.put(path, new JspFormFieldEvidence(
                    path, "form:" + field.group(1), false, false, jspPath));
        }
        Matcher errors = SPRING_FORM_ERRORS.matcher(body);
        while (errors.find()) {
            Map<String, String> attributes = attributes(errors.group(1));
            String path = attributes.get("path");
            if (path == null) {
                continue;
            }
            JspFormFieldEvidence existing = fields.get(path);
            fields.put(path, existing == null
                    ? new JspFormFieldEvidence(path, "form:errors", false, true, jspPath)
                    : new JspFormFieldEvidence(
                            existing.fieldName(), existing.tagName(), existing.hidden(), true, jspPath));
        }
        return new JspFormEvidence(
                formAttributes.get("name"),
                formAttributes.get("modelAttribute"),
                formAttributes.get("action"),
                resolveRoute(formAttributes.get("action")),
                formAttributes.get("method"),
                List.copyOf(fields.values()),
                sourceLocation(jspPath, offset));
    }

    private JspFormEvidence toPlainFormEvidence(
            Map<String, String> formAttributes, String body, String jspPath, int offset) {
        Map<String, JspFormFieldEvidence> fields = new LinkedHashMap<>();
        collectPlainFields(PLAIN_INPUT.matcher(body), fields, jspPath, true);
        collectPlainFields(PLAIN_SELECT.matcher(body), fields, jspPath, false);
        collectPlainFields(PLAIN_TEXTAREA.matcher(body), fields, jspPath, false);
        return new JspFormEvidence(
                formAttributes.get("name"),
                null,
                formAttributes.get("action"),
                resolveRoute(formAttributes.get("action")),
                formAttributes.get("method"),
                List.copyOf(fields.values()),
                sourceLocation(jspPath, offset));
    }

    private void collectPlainFields(
            Matcher matcher, Map<String, JspFormFieldEvidence> fields, String jspPath, boolean isInput) {
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group(1));
            String name = attributes.get("name");
            if (name == null) {
                continue;
            }
            String type = attributes.getOrDefault("type", "text").toLowerCase(Locale.ROOT);
            if (isInput && Set.of("submit", "button", "reset", "image").contains(type)) {
                continue;
            }
            boolean hidden = isInput && "hidden".equals(type);
            fields.put(name, new JspFormFieldEvidence(name, "input", hidden, false, jspPath));
        }
    }

    private List<JspModelReferenceEvidence> readModelReferences(
            String content, Set<String> loopScopedNames, String jspPath) {
        Map<String, JspModelReferenceEvidence> references = new LinkedHashMap<>();
        Matcher expression = EL_EXPRESSION.matcher(content);
        while (expression.find()) {
            String body = expression.group(1);
            Matcher path = EL_PATH_EXPRESSION.matcher(body);
            while (path.find()) {
                String root = path.group(1);
                if (isIgnorableRoot(root, loopScopedNames)) {
                    continue;
                }
                references.putIfAbsent(root, new JspModelReferenceEvidence(
                        root, "EL_EXPRESSION", sourceLocation(jspPath, content, expression.start())));
            }
        }
        return List.copyOf(references.values());
    }

    private List<JspDisplayFieldEvidence> readDisplayFields(String content, String jspPath) {
        List<JspDisplayFieldEvidence> displayFields = new ArrayList<>();
        Matcher matcher = TWO_SEGMENT_EL.matcher(content);
        while (matcher.find()) {
            displayFields.add(new JspDisplayFieldEvidence(
                    matcher.group(1), matcher.group(2), sourceLocation(jspPath, content, matcher.start())));
        }
        return displayFields;
    }

    private List<String> readRouteReferences(String content) {
        Set<String> routes = new LinkedHashSet<>();
        Matcher matcher = C_URL_VALUE.matcher(content);
        while (matcher.find()) {
            routes.add(matcher.group(1));
        }
        return List.copyOf(routes);
    }

    private boolean isIgnorableRoot(String root, Set<String> loopScopedNames) {
        return EL_RESERVED_WORDS.contains(root)
                || JSP_IMPLICIT_OBJECTS.contains(root)
                || loopScopedNames.contains(root)
                || root.chars().allMatch(Character::isDigit);
    }

    private String resolveRoute(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return null;
        }
        Matcher cUrl = C_URL_VALUE.matcher(rawAction);
        if (cUrl.find()) {
            return cUrl.group(1);
        }
        String contextPathToken = "${pageContext.request.contextPath}";
        if (rawAction.contains(contextPathToken)) {
            return rawAction.substring(rawAction.indexOf(contextPathToken) + contextPathToken.length());
        }
        return rawAction;
    }

    private String elRoot(String elExpression) {
        Matcher path = EL_PATH_EXPRESSION.matcher(elExpression);
        return path.find() ? path.group(1) : null;
    }

    private Map<String, String> attributes(String tagBody) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(tagBody);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

    private String sourceLocation(String jspPath, int offset) {
        return jspPath + "#" + offset;
    }

    private String sourceLocation(String jspPath, String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return jspPath + ":" + line;
    }
}
