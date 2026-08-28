package com.krdevops.springai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.krdevops.springai.config.mcp.McpAuthorizingToolCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
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
     * 2026-08-02 기준 실측치 — src/main/java/.../tools/** 아래 실제 {@code @Tool} 어노테이션
     * 메서드 수. {@code tools/AGENTS.md} 등 소스가 아닌 파일에 나오는 "@Tool(" 언급 2건은 세지
     * 않는다. Spring 컨텍스트에 등록된 {@code ToolCallback[]}의 길이가 가장 신뢰도 높은 런타임
     * 진실이므로 이 값으로 검증한다. 이 상수가 깨지면 Tool이 의도적으로 추가/삭제된 것인지
     * 확인한 뒤 값을 갱신하라.
     * R6-032~038 업데이트: 79 → 86 (FigmaDesignOrchestrationTool 7개 메서드 추가)
     * I-6 업데이트: 86 → 91 (FigmaThymeleafBridgeTool 5개 메서드 추가) → ARCH-WP2에서 제거
     * 승인 화면명세 Bundle 1개 + Thymeleaf Workflow 5개 추가: 91 → 97
     * ARCH-WP2 (RISK-02 Prototype Bridge 격리) 업데이트: 97 → 92
     * (FigmaThymeleafBridgeTool 5개 메서드 제거 — 실제 검증 없이 VERIFIED를 반환하거나
     * 임의 서버 경로를 읽는 등 운영 Tool로 볼 수 없어 MCP 등록에서 완전히 제거했다.
     * 재구현은 DesignParityValidationUseCase/ApprovedProjectWritePort 계약 확정 후
     * 별도 MCP 계약 버전에서 진행한다.)
     * WP8 3차 pass 업데이트: 92 → 94 (approveThymeleafBaseline,
     * revalidateThymeleafProjectWithBrowserGate)
     * WP6 생성 진입점 결선: 94 → 95 (previewThymeleafBindingGeneration)
     * R6-046/R5-045: 95 → 97 (previewPlatformConversion, previewStyleTokenDiff)
     */
    // R6-032~038(2026-08-18): generateFigmaBundleForOperation 추가로 97 → 98.
    // R6-065(2026-08-19): bindFigmaDesignRequestTable 추가로 98 → 99.
    // R8(04번 문서 §11, 2026-08-20): captureWebPageMultiViewport 추가로 99 → 100.
    // R8 Part B(04번 문서 §11, 2026-08-20): prepareFigmaBundleImport 추가로 100 → 101.
    // Region Ownership Task 8: adoptCurrentAsBaseline 추가로 101 → 102.
    // 픽셀재현 제외범위 구현계획 트랙 A/B: compareDesignFidelity/downloadFigmaAssets 추가로 102 → 104.
    // 픽셀재현 2차구현 반응형검증 구현계획: checkResponsiveRegression 추가로 104 → 105.
    private static final int EXPECTED_TOOL_METHOD_COUNT = 105;

    /**
     * McpConfig.allToolCallbacks(...)의 toolObjects(...) 인자 개수(등록된 *Tool 컴포넌트 클래스 수).
     * MethodToolCallback이 내부에 보관하는 toolObject의 실제 클래스를 리플렉션으로 읽어
     * distinct class 개수로 검증한다(2026-08-02 기준 실측치).
     * R6-039 업데이트: 31 → 32 (FigmaDesignOrchestrationTool 추가)
     * I-6 업데이트: 32 → 33 (FigmaThymeleafBridgeTool 추가) → ARCH-WP2에서 제거
     * ARCH-WP2 업데이트: 35 → 34 (FigmaThymeleafBridgeTool 제거)
     * WP8 3차 pass 업데이트: 34 → 35 (ThymeleafBaselineApprovalTool 추가)
     * WP6 생성 진입점 결선: 35 → 36 (ThymeleafBindingGenerationTool 추가)
     * Region Ownership Task 8: 36 → 37 (CrudGenerationSnapshotTool 추가)
     * 픽셀재현 제외범위 구현계획 트랙 A/B: 37 → 39 (DesignFidelityTool, FigmaAssetDownloadTool 추가)
     * 픽셀재현 2차구현 반응형검증 구현계획: 39 → 40 (ResponsiveRegressionTool 추가)
     */
    private static final int EXPECTED_TOOL_OBJECT_COUNT = 40;

    @Autowired
    private ToolCallbackProvider allToolCallbacks;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private McpRegisteredToolCatalog registeredToolCatalog;

    @Test
    void toolMethodCount_matchesRegisteredContract() {
        // AGENTS.md 등 문서상의 "@Tool(" 언급은 세지 않는다 — 여기서는 실제로 Spring이
        // MethodToolCallbackProvider로 등록한 콜백 개수(런타임 진실)만 센다.
        assertThat(allToolCallbacks.getToolCallbacks()).hasSize(EXPECTED_TOOL_METHOD_COUNT);
    }

    @Test
    void toolObjectCount_matchesRegisteredContract() throws Exception {
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

    @Test
    void toolSnapshotHash_matchesOperationalBaselineWhenConfigured() {
        String expected = System.getenv("MCP_TOOL_SNAPSHOT_HASH");
        Assumptions.assumeTrue(expected != null && !expected.isBlank(),
                "MCP_TOOL_SNAPSHOT_HASH가 설정된 운영 CI에서만 baseline hash를 비교합니다.");
        assertThat(registeredToolCatalog.matchesSnapshot(expected))
                .as("운영 MCP Tool snapshot hash가 baseline과 다릅니다")
                .isTrue();
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
     * 컴포넌트 클래스를 반환한다. ARCH-WP1(McpConfig)부터는 모든 콜백이
     * {@code McpAuthorizingToolCallback}으로 한 겹 감싸져 있으므로 먼저 원본을 꺼낸 뒤
     * MethodToolCallback이어야 한다는 전제를 검증한다.
     */
    private Class<?> resolveToolObjectClass(ToolCallback callback) throws Exception {
        ToolCallback unwrapped = callback instanceof McpAuthorizingToolCallback authorizing
                ? authorizing.delegate()
                : callback;
        if (!(unwrapped instanceof MethodToolCallback)) {
            throw new IllegalStateException(
                    "예상치 못한 ToolCallback 구현체: " + unwrapped.getClass()
                    + " — McpConfig가 MethodToolCallbackProvider만 사용한다는 전제가 깨졌습니다.");
        }
        Field field = MethodToolCallback.class.getDeclaredField("toolObject");
        field.setAccessible(true);
        return field.get(unwrapped).getClass();
    }
}
