package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FigmaDesignSpecMapper {

    public UiDesignSpec map(FigmaNodeDocument source, String featureType) {
        JsonNode root = source.document();
        validateRootNode(root);
        List<NodeInfo> nodes = new ArrayList<>();
        collect(root, nodes);
        List<String> uncertainties = new ArrayList<>();
        if (nodes.stream().anyMatch(node -> node.rotation() != 0)) {
            uncertainties.add("회전된 레이어가 있어 기하 바운딩 박스를 기준으로 해석했습니다.");
        }
        if (nodes.stream().anyMatch(node -> node.raw().path("clipsContent").asBoolean(false))) {
            uncertainties.add("클리핑 컨테이너 내부 요소는 가시 영역과 기하 바운딩 박스가 다를 수 있습니다.");
        }
        if (nodes.stream().anyMatch(node -> node.raw().path("effects").isArray()
                && !node.raw().path("effects").isEmpty())) {
            uncertainties.add("그림자·블러 등 effect가 있어 실제 렌더 영역은 기하 바운딩 박스와 다를 수 있습니다.");
        }
        if (nodes.stream().anyMatch(this::renderBoundsDiffer)) {
            uncertainties.add("absoluteRenderBounds와 absoluteBoundingBox가 달라 레이아웃은 기하 좌표를 기준으로 해석했습니다.");
        }
        if (nodes.stream().anyMatch(node -> !node.visible())) {
            uncertainties.add("비가시 레이어는 화면 구조 분석에서 제외했습니다.");
        }

        List<NodeInfo> visibleNodes = nodes.stream().filter(NodeInfo::visible).toList();
        List<UiDesignSpec.ComponentSpec> components = components(visibleNodes, uncertainties);
        List<UiDesignSpec.FieldHint> fields = fields(visibleNodes, uncertainties);
        List<UiDesignSpec.ActionSpec> actions = actions(visibleNodes);
        UiDesignSpec.LayoutSpec layout = layout(root, visibleNodes, actions);
        Map<String, String> tokens = tokens(root);

        if (components.isEmpty()) uncertainties.add("표준 UI 컴포넌트를 확정할 수 없습니다.");
        if (fields.isEmpty()) uncertainties.add("시맨틱 필드 역할을 확정할 수 없습니다.");
        List<UiDesignSpec.NodeGeometry> geometryTree = List.of(buildGeometryTree(root, uncertainties));
        return new UiDesignSpec(archetype(root.path("name").asText(""), featureType), layout,
                components, actions, fields, tokens, interactions(visibleNodes),
                List.copyOf(new LinkedHashSet<>(uncertainties)), geometryTree);
    }

    private void validateRootNode(JsonNode root) {
        String type = root.path("type").asText("").trim().toUpperCase(Locale.ROOT);
        if ("FRAME".equals(type)) return;
        if ("SECTION".equals(type) || "CANVAS".equals(type) || "DOCUMENT".equals(type)) {
            throw new FigmaApiException("FIGMA_FRAME_REQUIRED", 422,
                    "여러 화면을 포함할 수 있는 Figma " + type
                            + " 노드는 분석할 수 없습니다. 단일 화면 FRAME의 node-id를 지정하세요.");
        }
        throw new FigmaApiException("FIGMA_UNSUPPORTED_NODE_TYPE", 422,
                "지원하지 않는 Figma 루트 노드 타입입니다. 단일 화면 FRAME의 node-id를 지정하세요.");
    }

    private void collect(JsonNode node, List<NodeInfo> result) {
        String type = node.path("type").asText("");
        String name = node.path("name").asText("");
        String characters = node.path("characters").asText("");
        JsonNode box = node.path("absoluteBoundingBox");
        result.add(new NodeInfo(type, name, characters,
                box.path("x").asDouble(0), box.path("y").asDouble(0),
                box.path("width").asDouble(0), box.path("height").asDouble(0),
                node.path("rotation").asDouble(0), node.path("visible").asBoolean(true), node));
        JsonNode children = node.path("children");
        if (children.isArray()) children.forEach(child -> collect(child, result));
    }

    private boolean renderBoundsDiffer(NodeInfo node) {
        JsonNode geometry = node.raw().path("absoluteBoundingBox");
        JsonNode render = node.raw().path("absoluteRenderBounds");
        if (!geometry.isObject() || !render.isObject()) return false;
        return differs(geometry, render, "x") || differs(geometry, render, "y")
                || differs(geometry, render, "width") || differs(geometry, render, "height");
    }

    private boolean differs(JsonNode left, JsonNode right, String field) {
        return Math.abs(left.path(field).asDouble() - right.path(field).asDouble()) > 0.5d;
    }

    private List<UiDesignSpec.ComponentSpec> components(
            List<NodeInfo> nodes, List<String> uncertainties) {
        Map<String, LinkedHashSet<String>> values = new LinkedHashMap<>();
        // 타입별로 처음 매치된 노드의 색상만 대표값으로 채택한다(여러 노드가 같은 타입으로 묶이는
        // 그룹핑 구조이므로, 노드별 개별 색상까지는 반영하지 않는다 — 구현계획서 §6 리스크 참고).
        Map<String, String[]> colorsByType = new LinkedHashMap<>();
        for (NodeInfo node : nodes) {
            String normalized = text(node);
            String componentType = null;
            if (containsAny(normalized, "table", "list", "grid", "목록", "테이블")) componentType = "TABLE";
            else if (containsAny(normalized, "search", "filter", "검색", "필터")) componentType = "SEARCH_PANEL";
            else if (containsAny(normalized, "form", "input", "field", "등록", "수정", "입력")) componentType = "FORM";
            else if (containsAny(normalized, "pagination", "paging", "페이지")) componentType = "PAGINATION";
            else if (containsAny(normalized, "button", "actions", "버튼")) componentType = "ACTION_GROUP";
            if (componentType != null) {
                values.computeIfAbsent(componentType, ignored -> new LinkedHashSet<>())
                        .add(node.name().isBlank() ? node.type() : node.name());
                colorsByType.computeIfAbsent(componentType,
                        ignored -> new String[] {solidFillColor(node), solidStrokeColor(node)});
            } else if (("COMPONENT".equals(node.type()) || "INSTANCE".equals(node.type()))
                    && genericName(node.name())) {
                uncertainties.add("의미를 알 수 없는 컴포넌트가 있습니다: " + safeLabel(node.name()));
            }
        }
        return values.entrySet().stream()
                .map(entry -> {
                    String[] colors = colorsByType.get(entry.getKey());
                    return new UiDesignSpec.ComponentSpec(
                            entry.getKey(), List.copyOf(entry.getValue()), colors[0], colors[1]);
                })
                .toList();
    }

    /** {@code fills} 배열에서 첫 번째 SOLID·visible 페인트만 rgba 문자열로 반환한다. 그 외 타입
     * (GRADIENT_LINEAR/IMAGE 등)은 무시한다 — 구현계획서 §6 리스크 참고. */
    private @Nullable String solidFillColor(NodeInfo node) {
        return firstSolidPaint(node.raw().path("fills"));
    }

    /** {@code strokes} 배열에서 첫 번째 SOLID·visible 페인트만 rgba 문자열로 반환한다. */
    private @Nullable String solidStrokeColor(NodeInfo node) {
        return firstSolidPaint(node.raw().path("strokes"));
    }

    private @Nullable String firstSolidPaint(JsonNode paints) {
        if (!paints.isArray()) return null;
        for (JsonNode paint : paints) {
            if ("SOLID".equals(paint.path("type").asText())
                    && paint.path("visible").asBoolean(true)
                    && paint.path("color").isObject()) {
                return rgba(paint.path("color"));
            }
        }
        return null;
    }

    /** 노드 트리를 부모-자식 구조 그대로 보존하며 좌표·스타일을 추출한다({@code collect()}는
     * 평탄화하므로 재사용하지 않는다 — 구현계획서 §3/§5.1 참고). 비가시 노드는 트리에서 제외한다. */
    private UiDesignSpec.NodeGeometry buildGeometryTree(JsonNode node, List<String> uncertainties) {
        JsonNode box = node.path("absoluteBoundingBox");
        List<UiDesignSpec.NodeGeometry> children = new ArrayList<>();
        for (JsonNode child : node.path("children")) {
            if (!child.path("visible").asBoolean(true)) continue;
            children.add(buildGeometryTree(child, uncertainties));
        }
        children = collapseRepeatedSiblings(children, uncertainties);

        return new UiDesignSpec.NodeGeometry(
                node.path("id").asText(""), node.path("type").asText(""), node.path("name").asText(""),
                box.path("x").asDouble(0), box.path("y").asDouble(0),
                box.path("width").asDouble(0), box.path("height").asDouble(0),
                node.path("cornerRadius").isNumber() ? node.path("cornerRadius").asInt() : null,
                node.path("opacity").isNumber() ? node.path("opacity").asDouble() : null,
                firstSolidPaint(node.path("fills")), firstSolidPaint(node.path("strokes")),
                autoLayoutOf(node), textStyleOf(node), children);
    }

    private UiDesignSpec.NodeGeometry.@Nullable AutoLayout autoLayoutOf(JsonNode node) {
        String mode = node.path("layoutMode").asText("NONE");
        if ("NONE".equals(mode)) return null;
        return new UiDesignSpec.NodeGeometry.AutoLayout(
                mode, node.path("itemSpacing").asDouble(0),
                node.path("paddingTop").asDouble(0), node.path("paddingRight").asDouble(0),
                node.path("paddingBottom").asDouble(0), node.path("paddingLeft").asDouble(0));
    }

    private UiDesignSpec.NodeGeometry.@Nullable TextStyle textStyleOf(JsonNode node) {
        if (!"TEXT".equals(node.path("type").asText())) return null;
        JsonNode style = node.path("style");
        if (!style.isObject()) return null;
        return new UiDesignSpec.NodeGeometry.TextStyle(
                style.path("fontFamily").isTextual() ? style.path("fontFamily").asText() : null,
                style.path("fontSize").isNumber() ? style.path("fontSize").asDouble() : null,
                style.path("fontWeight").isNumber() ? style.path("fontWeight").asDouble() : null,
                style.path("lineHeightPx").isNumber() ? style.path("lineHeightPx").asDouble() : null);
    }

    /** 같은 부모 아래 type+name이 완전히 같은 형제가 3개 이상 연속되면(표의 반복 행 등) 첫 번째만
     * 대표로 남긴다 — 구현계획서 §5.3 정제 규칙. */
    private List<UiDesignSpec.NodeGeometry> collapseRepeatedSiblings(
            List<UiDesignSpec.NodeGeometry> siblings, List<String> uncertainties) {
        List<UiDesignSpec.NodeGeometry> result = new ArrayList<>();
        int i = 0;
        while (i < siblings.size()) {
            UiDesignSpec.NodeGeometry current = siblings.get(i);
            int runLength = 1;
            while (i + runLength < siblings.size() && isSameShape(current, siblings.get(i + runLength))) {
                runLength++;
            }
            result.add(current);
            if (runLength >= 3) {
                uncertainties.add("반복 패턴 " + runLength + "개 중 1개만 대표로 반영했습니다: "
                        + safeLabel(current.name().isBlank() ? current.type() : current.name()));
            } else {
                for (int k = 1; k < runLength; k++) result.add(siblings.get(i + k));
            }
            i += runLength;
        }
        return result;
    }

    private boolean isSameShape(UiDesignSpec.NodeGeometry left, UiDesignSpec.NodeGeometry right) {
        return left.type().equals(right.type()) && left.name().equals(right.name());
    }

    private List<UiDesignSpec.FieldHint> fields(
            List<NodeInfo> nodes, List<String> uncertainties) {
        List<UiDesignSpec.FieldHint> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (NodeInfo node : nodes) {
            if (!"TEXT".equals(node.type())) continue;
            String label = node.characters().isBlank() ? node.name() : node.characters();
            UiFieldRole role = role(text(node));
            if (role == UiFieldRole.GENERIC) continue;
            String id = uniqueId(camel(node.name().isBlank() ? label : node.name()), ids);
            result.add(new UiDesignSpec.FieldHint(id, label, role, control(role), 0.9));
        }
        if (result.isEmpty() && nodes.stream().anyMatch(node -> "TEXT".equals(node.type()))) {
            uncertainties.add("텍스트 레이어는 있으나 표준 필드 역할로 분류되지 않았습니다.");
        }
        return List.copyOf(result);
    }

    private List<UiDesignSpec.ActionSpec> actions(List<NodeInfo> nodes) {
        Map<String, String> actions = new LinkedHashMap<>();
        for (NodeInfo node : nodes) {
            String text = text(node);
            action(text, "CREATE", "PRIMARY", actions, "create", "new", "register", "등록", "추가");
            action(text, "SAVE", "PRIMARY", actions, "save", "저장");
            action(text, "UPDATE", "PRIMARY", actions, "update", "edit", "수정");
            action(text, "DELETE", "DANGER", actions, "delete", "remove", "삭제");
            action(text, "SEARCH", "SECONDARY", actions, "search", "조회", "검색");
            action(text, "APPLY", "PRIMARY", actions, "apply", "신청하기");
            action(text, "BACK", "SECONDARY", actions, "back", "목록으로", "뒤로");
            action(text, "CANCEL", "SECONDARY", actions, "cancel", "취소");
        }
        return actions.entrySet().stream()
                .map(entry -> new UiDesignSpec.ActionSpec(entry.getKey(), entry.getValue()))
                .toList();
    }

    private UiDesignSpec.LayoutSpec layout(
            JsonNode root, List<NodeInfo> nodes, List<UiDesignSpec.ActionSpec> actions) {
        JsonNode box = root.path("absoluteBoundingBox");
        double width = box.path("width").asDouble(0);
        String contentWidth = width >= 1440 ? "wide" : width >= 1024 ? "standard" : "narrow";
        double area = Math.max(1, width * Math.max(1, box.path("height").asDouble(0)));
        double elementDensity = nodes.size() / area * 1_000_000d;
        String density = elementDensity > 35 ? "compact" : elementDensity < 12 ? "comfortable" : "standard";
        String formColumns = twoColumn(nodes) ? "two-column" : "single-column";
        boolean search = nodes.stream().anyMatch(node -> containsAny(text(node), "search", "filter", "검색", "필터"));
        String actionPlacement = actionPlacement(root, nodes, actions);
        return new UiDesignSpec.LayoutSpec("figma-reference", contentWidth, density,
                formColumns, actionPlacement, search ? "above-table" : "none");
    }

    private boolean twoColumn(List<NodeInfo> nodes) {
        List<NodeInfo> fieldNodes = nodes.stream()
                .filter(node -> "TEXT".equals(node.type()) && role(text(node)) != UiFieldRole.GENERIC)
                .toList();
        for (int i = 0; i < fieldNodes.size(); i++) {
            for (int j = i + 1; j < fieldNodes.size(); j++) {
                NodeInfo left = fieldNodes.get(i);
                NodeInfo right = fieldNodes.get(j);
                if (Math.abs(left.y() - right.y()) <= 12 && Math.abs(left.x() - right.x()) > 120) return true;
            }
        }
        return false;
    }

    private String actionPlacement(JsonNode root, List<NodeInfo> nodes, List<UiDesignSpec.ActionSpec> actions) {
        if (actions.isEmpty()) return "top-right";
        JsonNode box = root.path("absoluteBoundingBox");
        double middleY = box.path("y").asDouble(0) + box.path("height").asDouble(0) / 2;
        return nodes.stream()
                .filter(node -> containsAny(text(node), "create", "register", "등록", "추가"))
                .map(node -> node.y() > middleY ? "bottom-right" : "top-right")
                .findFirst().orElse("top-right");
    }

    private Map<String, String> tokens(JsonNode root) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode background = root.path("backgroundColor");
        if (background.isObject()) result.put("backgroundColor", rgba(background));
        JsonNode style = root.path("style");
        if (style.path("fontFamily").isTextual()) result.put("fontFamily", style.path("fontFamily").asText());
        if (style.path("fontSize").isNumber()) result.put("fontSize", style.path("fontSize").asText());
        return Map.copyOf(result);
    }

    private List<UiDesignSpec.InteractionSpec> interactions(List<NodeInfo> nodes) {
        List<UiDesignSpec.InteractionSpec> result = new ArrayList<>();
        for (NodeInfo node : nodes) {
            JsonNode interactions = node.raw().path("interactions");
            if (!interactions.isArray()) continue;
            interactions.forEach(interaction -> {
                String trigger = interaction.path("trigger").path("type").asText("");
                String action = interaction.path("actions").isArray()
                        && !interaction.path("actions").isEmpty()
                        ? interaction.path("actions").get(0).path("type").asText("") : "";
                if (!trigger.isBlank() && !action.isBlank()) {
                    result.add(new UiDesignSpec.InteractionSpec(trigger, action));
                }
            });
        }
        return List.copyOf(result);
    }

    private String archetype(String rootName, String featureType) {
        String text = (rootName + " " + (featureType == null ? "" : featureType)).toLowerCase(Locale.ROOT);
        if (containsAny(text, "master", "detail", "마스터")) return "MASTER_DETAIL";
        String prefix = containsAny(text, "board", "게시판", "게시") ? "BOARD" : "CRUD";
        if (containsAny(text, "form", "register", "edit", "등록", "수정")) return prefix + "_FORM";
        if (containsAny(text, "detail", "view", "상세")) return prefix + "_DETAIL";
        return prefix + "_LIST";
    }

    private UiFieldRole role(String text) {
        if (containsAny(text, "row number", "rownum", "번호")) return UiFieldRole.ROW_NUMBER;
        if (containsAny(text, "title", "subject", "제목")) return UiFieldRole.TITLE;
        if (containsAny(text, "content", "description", "내용", "본문")) return UiFieldRole.CONTENT;
        if (containsAny(text, "category", "type", "분류", "유형")) return UiFieldRole.CATEGORY;
        if (containsAny(text, "status", "state", "상태")) return UiFieldRole.STATUS;
        if (containsAny(text, "author", "writer", "작성자")) return UiFieldRole.AUTHOR;
        if (containsAny(text, "department", "dept", "부서")) return UiFieldRole.DEPARTMENT;
        if (containsAny(text, "created", "등록일", "작성일")) return UiFieldRole.CREATED_AT;
        if (containsAny(text, "updated", "수정일")) return UiFieldRole.UPDATED_AT;
        if (containsAny(text, "attachment", "file", "첨부")) return UiFieldRole.ATTACHMENT;
        if (containsAny(text, "sort", "order", "순서")) return UiFieldRole.SORT_ORDER;
        if (containsAny(text, " id", "id ", "identifier", "식별자")) return UiFieldRole.ID;
        return UiFieldRole.GENERIC;
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

    private void action(String text, String type, String importance,
                        Map<String, String> result, String... keywords) {
        if (containsAny(text, keywords)) result.putIfAbsent(type, importance);
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) if (value.contains(keyword)) return true;
        return false;
    }

    private String text(NodeInfo node) {
        return (node.name() + " " + node.characters()).toLowerCase(Locale.ROOT);
    }

    private boolean genericName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.matches("(rectangle|frame|component|instance)[ _-]*[0-9]+.*");
    }

    private String safeLabel(String name) {
        if (name == null || name.isBlank()) return "이름 없음";
        return name.length() <= 60 ? name : name.substring(0, 60);
    }

    private String uniqueId(String base, Set<String> used) {
        String candidate = base.isBlank() ? "field" : base;
        String result = candidate;
        int suffix = 2;
        while (!used.add(result)) result = candidate + suffix++;
        return result;
    }

    private String camel(String value) {
        String[] parts = (value == null ? "" : value).trim().split("[^A-Za-z0-9가-힣]+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            String lower = part.toLowerCase(Locale.ROOT);
            if (result.isEmpty()) result.append(lower);
            else result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return result.toString();
    }

    private String rgba(JsonNode color) {
        int r = (int) Math.round(color.path("r").asDouble(0) * 255);
        int g = (int) Math.round(color.path("g").asDouble(0) * 255);
        int b = (int) Math.round(color.path("b").asDouble(0) * 255);
        double a = color.path("a").asDouble(1);
        return "rgba(%d,%d,%d,%.2f)".formatted(r, g, b, a);
    }

    private record NodeInfo(
            String type, String name, String characters,
            double x, double y, double width, double height,
            double rotation, boolean visible, JsonNode raw) {
    }
}
