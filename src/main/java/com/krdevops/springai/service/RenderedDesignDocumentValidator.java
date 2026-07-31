package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RenderedDesignDocumentValidator {
    private final ObjectMapper objectMapper;
    private final RenderedDesignSchemaValidator schemaValidator;

    public RenderedDesignDocumentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaValidator = new RenderedDesignSchemaValidator();
    }

    public void validate(RenderedDesignDocument document, String captureId, String documentKey) {
        try {
            schemaValidator.validate(objectMapper.writeValueAsString(document));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("RenderedDesignDocument 직렬화 검증 실패", e);
        }
        if (document == null || !RenderedDesignDocument.SCHEMA_VERSION.equals(document.schemaVersion())) {
            throw new IllegalArgumentException("지원하지 않는 rendered design schema입니다.");
        }
        if (!captureId.equals(document.captureId()) || !documentKey.equals(document.documentKey())) {
            throw new IllegalArgumentException("captureId 또는 documentKey가 요청과 일치하지 않습니다.");
        }
        if (document.source() == null || document.environment() == null || document.page() == null) {
            throw new IllegalArgumentException("source, environment, page는 필수입니다.");
        }
        if (document.nodes().size() > 100_000 || document.assets().size() > 5_000) {
            throw new IllegalArgumentException("document 항목 수 제한을 초과했습니다.");
        }
        Set<String> ids = new HashSet<>();
        for (RenderedNode node : document.nodes()) {
            if (node.id() == null || !ids.add(node.id())) throw new IllegalArgumentException("node ID가 중복되거나 비어 있습니다.");
            if (node.bounds() != null && (node.bounds().width() < 0 || node.bounds().height() < 0
                    || !Double.isFinite(node.bounds().x()) || !Double.isFinite(node.bounds().y())
                    || !Double.isFinite(node.bounds().width()) || !Double.isFinite(node.bounds().height()))) {
                throw new IllegalArgumentException("유효하지 않은 node geometry입니다.");
            }
        }
        for (RenderedNode node : document.nodes()) {
            if (node.parentId() != null && !ids.contains(node.parentId())) throw new IllegalArgumentException("parent node 참조가 없습니다.");
            if (node.children().stream().anyMatch(id -> !ids.contains(id))) throw new IllegalArgumentException("child node 참조가 없습니다.");
        }
        java.util.Map<String, RenderedNode> byId = document.nodes().stream()
                .collect(java.util.stream.Collectors.toMap(RenderedNode::id, node -> node));
        for (RenderedNode node : document.nodes()) {
            for (String childId : node.children()) {
                if (!node.id().equals(byId.get(childId).parentId())) {
                    throw new IllegalArgumentException("parent/child 양방향 참조가 일치하지 않습니다.");
                }
            }
            detectCycle(node.id(), byId, new HashSet<>(), new HashSet<>());
        }
        Set<String> assetIds = new HashSet<>();
        document.assets().forEach(asset -> {
            if (!ids.contains(asset.id()) || !assetIds.add(asset.id())) {
                throw new IllegalArgumentException("asset의 node 참조가 없거나 중복됩니다.");
            }
            if (asset.path() == null || !asset.path().matches("assets/[A-Za-z0-9._/-]+")
                    || asset.byteLength() < 0 || asset.byteLength() > 5L * 1024 * 1024
                    || asset.contentHash() == null || !asset.contentHash().matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("asset 메타데이터가 올바르지 않습니다.");
            }
        });
        if (document.contentHash() == null || !document.contentHash().matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("contentHash 형식이 올바르지 않습니다.");
        }
        if (!document.contentHash().equals(calculateContentHash(document))) {
            throw new IllegalArgumentException("contentHash가 정규화된 문서 내용과 일치하지 않습니다.");
        }
    }

    private void detectCycle(String id, java.util.Map<String, RenderedNode> nodes,
                             Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("node tree 순환이 존재합니다.");
        for (String child : nodes.get(id).children()) detectCycle(child, nodes, visiting, visited);
        visiting.remove(id);
        visited.add(id);
    }

    private static final Pattern COLOR_PATTERN = Pattern.compile("^rgba?\\(([^)]+)\\)$", Pattern.CASE_INSENSITIVE);

    /**
     * jsp-design-extractor의 server.ts(normalizeForHash/sortByStableKey)와 반드시 동일한 canonicalization을
     * 수행해야 한다. 두 구현이 갈라지면 extractor가 계산한 contentHash를 springai가 재검증할 때마다 실패한다.
     */
    public String calculateContentHash(RenderedDesignDocument document) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.valueToTree(document);
            root.remove("contentHash");
            root.remove("captureId");
            JsonNode source = root.get("source");
            if (source instanceof ObjectNode objectSource) objectSource.remove("capturedAt");
            sortStableArray(root, "componentCandidates", item -> item.path("type").asText("")
                    + "|" + sortedJoin(item.path("nodeIds")));
            sortStableArray(root, "assets", item -> {
                String hash = item.path("contentHash").asText("");
                return hash.isEmpty() ? item.path("id").asText("") : hash;
            });
            sortStableArray(root, "warnings", item -> item.path("code").asText("")
                    + "|" + item.path("nodeId").asText(""));
            byte[] bytes = objectMapper.writeValueAsString(canonicalNode(root)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("contentHash 계산 실패", e);
        }
    }

    private static void sortStableArray(ObjectNode root, String field, Function<JsonNode, String> keyOf) {
        if (!(root.get(field) instanceof ArrayNode array)) return;
        List<JsonNode> items = new ArrayList<>();
        array.forEach(items::add);
        items.sort(Comparator.comparing(keyOf));
        ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
        items.forEach(sorted::add);
        root.set(field, sorted);
    }

    private static String sortedJoin(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        values.sort(Comparator.naturalOrder());
        return String.join(",", values);
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            java.util.stream.StreamSupport.stream(
                            java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false)
                    .sorted().forEach(name -> result.set(name, canonicalNode(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(canonicalNode(item)));
            return result;
        }
        if (node.isNumber()) {
            double rounded = Math.round(node.asDouble() * 100.0) / 100.0;
            if (rounded == Math.rint(rounded) && !Double.isInfinite(rounded)) {
                return JsonNodeFactory.instance.numberNode((long) rounded);
            }
            return JsonNodeFactory.instance.numberNode(rounded);
        }
        if (node.isTextual()) {
            String normalized = Normalizer.normalize(node.asText(), Normalizer.Form.NFC);
            return JsonNodeFactory.instance.textNode(normalizeColor(normalized));
        }
        return node;
    }

    private static String normalizeColor(String value) {
        Matcher matcher = COLOR_PATTERN.matcher(value);
        if (!matcher.matches()) return value;
        String[] parts = matcher.group(1).split(",");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        String alpha = parts.length > 3 ? parts[3] : "1";
        return "rgba(%s, %s, %s, %s)".formatted(parts[0], parts[1], parts[2], alpha);
    }
}
