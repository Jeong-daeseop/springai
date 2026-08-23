package com.krdevops.springai.service.pipeline;

import com.krdevops.springai.model.artifact.ContentHashes;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 런타임에 실제 등록된 MCP Tool 계약을 조회해 baseline drift 검증에 사용한다.
 * snapshot hash는 이름뿐 아니라 설명과 입력 JSON Schema까지 포함한다.
 */
@Service
public class McpRegisteredToolCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ToolCallbackProvider provider;
    public McpRegisteredToolCatalog(ToolCallbackProvider provider) { this.provider = provider; }
    public List<String> toolNames() {
        return Arrays.stream(provider.getToolCallbacks()).map(callback -> callback.getToolDefinition().name()).sorted().toList();
    }
    public String snapshotHash() {
        // 이름만 해시하면 설명·입력 Schema 변경을 놓치므로 운영 baseline은 전체 Tool 계약을
        // 포함한다. Spring AI가 제공하는 정의를 이름으로 정렬해 등록 순서에는 영향을 받지 않는다.
        String contract = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> {
                    var definition = callback.getToolDefinition();
                    return definition.name() + "\n"
                            + String.valueOf(definition.description()) + "\n"
                            + canonicalJson(definition.inputSchema());
                })
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n---\n"));
        return ContentHashes.sha256Hex(contract.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalJson(String schema) {
        try {
            return normalize(JSON.readTree(schema)).toString();
        } catch (IOException | RuntimeException ignored) {
            // 계약이 JSON이 아닌 경우에도 hash 계산은 fail-closed로 계속한다.
            return schema;
        }
    }

    private static JsonNode normalize(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            node.forEach(child -> array.add(normalize(child)));
            return array;
        }
        if (!node.isObject()) return node;
        ObjectNode sorted = JSON.createObjectNode();
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        Collections.sort(fields);
        for (String field : fields) sorted.set(field, normalize(node.get(field)));
        return sorted;
    }
    public boolean matchesSnapshot(String expectedHash) {
        return expectedHash != null && expectedHash.matches("[0-9a-f]{64}") && snapshotHash().equals(expectedHash);
    }
}
