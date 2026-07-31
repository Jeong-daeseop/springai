package com.krdevops.springai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP Tool 계약(이름·설명·입력 JSON Schema) Characterization 스냅샷 테스트.
 *
 * <p>WP-1~WP-4 리팩터링(계획서 §6) 동안 기존 MCP Tool 이름·입력 Schema를 바꾸지 않는다는
 * ORT-PRN-007 원칙을 자동으로 감지하기 위한 회귀 기준선이다.
 * {@code src/test/resources/mcp/tool-definitions-baseline.json} 파일이 없으면 최초 실행 시
 * 현재 상태를 그대로 저장하고, 파일이 있으면 현재 상태가 저장된 baseline과 100% 일치하는지
 * 검증한다. Tool 계약이 의도적으로 바뀌었다면 baseline 파일을 삭제하고 테스트를 다시 실행해
 * 갱신한다.
 */
@SpringBootTest
class McpToolDefinitionSnapshotTest {

    private static final Path BASELINE_PATH =
            Path.of("src/test/resources/mcp/tool-definitions-baseline.json");

    /**
     * 2026-07-31 기준 실측치 — src/main/java/.../tools/** 아래 실제 {@code @Tool} 어노테이션
     * 메서드 수. {@code tools/AGENTS.md} 등 소스가 아닌 파일에 나오는 "@Tool(" 언급 2건은 세지
     * 않는다. Spring 컨텍스트에 등록된 {@code ToolCallback[]}의 길이가 가장 신뢰도 높은 런타임
     * 진실이므로 이 값으로 검증한다. 이 상수가 깨지면 Tool이 의도적으로 추가/삭제된 것인지
     * 확인한 뒤 값을 갱신하라.
     */
    private static final int EXPECTED_TOOL_METHOD_COUNT = 79;

    /**
     * McpConfig.allToolCallbacks(...)의 toolObjects(...) 인자 개수(등록된 *Tool 컴포넌트 클래스 수).
     * MethodToolCallback이 내부에 보관하는 toolObject의 실제 클래스를 리플렉션으로 읽어
     * distinct class 개수로 검증한다(2026-07-31 기준 실측치).
     */
    private static final int EXPECTED_TOOL_OBJECT_COUNT = 25;

    @Autowired
    private ToolCallbackProvider allToolCallbacks;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void toolMethodCount_is79() {
        // AGENTS.md 등 문서상의 "@Tool(" 언급은 세지 않는다 — 여기서는 실제로 Spring이
        // MethodToolCallbackProvider로 등록한 콜백 개수(런타임 진실)만 센다.
        assertThat(allToolCallbacks.getToolCallbacks()).hasSize(EXPECTED_TOOL_METHOD_COUNT);
    }

    @Test
    void toolObjectCount_is25() throws Exception {
        Set<Class<?>> distinctToolObjectClasses = new HashSet<>();
        for (ToolCallback callback : allToolCallbacks.getToolCallbacks()) {
            distinctToolObjectClasses.add(resolveToolObjectClass(callback));
        }
        assertThat(distinctToolObjectClasses).hasSize(EXPECTED_TOOL_OBJECT_COUNT);
    }

    @Test
    void toolNames_haveNoDuplicates() {
        List<String> names = Arrays.stream(allToolCallbacks.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .toList();
        assertThat(new HashSet<>(names)).hasSize(names.size());
    }

    @Test
    void toolDefinitions_matchStoredBaseline() throws IOException {
        ArrayNode current = currentSnapshot();

        if (!Files.exists(BASELINE_PATH)) {
            Files.createDirectories(BASELINE_PATH.getParent());
            Files.writeString(BASELINE_PATH,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current) + "\n",
                    StandardCharsets.UTF_8);
            return; // 최초 실행 — baseline을 현재 상태로 생성한다.
        }

        JsonNode stored = objectMapper.readTree(BASELINE_PATH.toFile());
        assertThat(current)
                .as("MCP Tool 계약(이름/설명/입력 JSON Schema)이 baseline과 달라졌습니다(ORT-PRN-007 위반 가능성). "
                        + "의도된 변경이라면 " + BASELINE_PATH + " 파일을 삭제한 뒤 이 테스트를 다시 실행해 갱신하세요.")
                .isEqualTo(stored);
    }

    /** Tool 이름 오름차순으로 정렬된 현재 Tool 계약 스냅샷을 만든다. */
    private ArrayNode currentSnapshot() {
        ArrayNode array = objectMapper.createArrayNode();
        Arrays.stream(allToolCallbacks.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .sorted(Comparator.comparing(ToolDefinition::name))
                .forEach(definition -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("name", definition.name());
                    node.put("description", definition.description());
                    node.set("inputSchema", normalizeKeysAscending(readSchema(definition.inputSchema())));
                    array.add(node);
                });
        return array;
    }

    private JsonNode readSchema(String inputSchemaJson) {
        try {
            return objectMapper.readTree(inputSchemaJson);
        } catch (IOException e) {
            throw new IllegalStateException("입력 JSON Schema 파싱 실패: " + inputSchemaJson, e);
        }
    }

    /** 객체 키를 오름차순으로 정규화해 저장한다(배열 원소 순서는 그대로 유지). */
    private JsonNode normalizeKeysAscending(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);
            for (String key : keys) {
                sorted.set(key, normalizeKeysAscending(node.get(key)));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode arr = objectMapper.createArrayNode();
            node.forEach(child -> arr.add(normalizeKeysAscending(child)));
            return arr;
        }
        return node;
    }

    /**
     * MethodToolCallback의 private {@code toolObject} 필드를 리플렉션으로 읽어 실제 Tool
     * 컴포넌트 클래스를 반환한다. McpConfig는 MethodToolCallbackProvider만 사용하므로
     * 모든 콜백이 MethodToolCallback이어야 한다는 전제를 명시적으로 검증한다.
     */
    private Class<?> resolveToolObjectClass(ToolCallback callback) throws Exception {
        if (!(callback instanceof MethodToolCallback)) {
            throw new IllegalStateException(
                    "예상치 못한 ToolCallback 구현체: " + callback.getClass()
                    + " — McpConfig가 MethodToolCallbackProvider만 사용한다는 전제가 깨졌습니다.");
        }
        Field field = MethodToolCallback.class.getDeclaredField("toolObject");
        field.setAccessible(true);
        return field.get(callback).getClass();
    }
}
