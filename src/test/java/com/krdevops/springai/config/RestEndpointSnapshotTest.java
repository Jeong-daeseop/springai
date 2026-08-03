package com.krdevops.springai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0004: REST endpoint(경로·HTTP method) Characterization 스냅샷.
 *
 * <p>{@code McpToolDefinitionSnapshotTest}와 같은 패턴 — baseline이 없으면 현재 상태를 저장하고,
 * 있으면 100% 일치를 요구한다. {@code @RestController} 클래스를 classpath 스캔으로 찾으므로
 * 새 Controller가 추가돼도 이 테스트가 자동으로 대상에 포함한다.
 */
class RestEndpointSnapshotTest {

    private static final Path BASELINE_PATH =
            Path.of("src/test/resources/rest/endpoint-inventory-baseline.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void restEndpoints_matchStoredBaseline() throws IOException {
        ArrayNode current = currentSnapshot();

        if (!Files.exists(BASELINE_PATH)) {
            Files.createDirectories(BASELINE_PATH.getParent());
            Files.writeString(BASELINE_PATH,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(current) + "\n",
                    StandardCharsets.UTF_8);
            return;
        }

        JsonNode stored = objectMapper.readTree(BASELINE_PATH.toFile());
        assertThat(current)
                .as("REST endpoint 목록이 baseline과 달라졌습니다. 의도된 변경이라면 "
                        + BASELINE_PATH + " 파일을 삭제한 뒤 이 테스트를 다시 실행해 갱신하세요.")
                .isEqualTo(stored);
    }

    private ArrayNode currentSnapshot() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<ObjectNode> entries = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.krdevops.springai.controller")) {
            Class<?> controllerClass;
            try {
                controllerClass = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }

            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                    controllerClass, RequestMapping.class);
            String basePath = (classMapping != null && classMapping.value().length > 0)
                    ? classMapping.value()[0] : "";

            for (Method method : controllerClass.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String subPath = mapping.value().length > 0 ? mapping.value()[0] : "";
                String httpMethod = mapping.method().length > 0 ? mapping.method()[0].name() : "UNSPECIFIED";

                ObjectNode node = objectMapper.createObjectNode();
                node.put("controller", controllerClass.getSimpleName());
                node.put("method", httpMethod);
                node.put("path", basePath + subPath);
                entries.add(node);
            }
        }

        entries.sort(Comparator
                .comparing((ObjectNode n) -> n.get("path").asText())
                .thenComparing(n -> n.get("method").asText())
                .thenComparing(n -> n.get("controller").asText()));

        ArrayNode array = objectMapper.createArrayNode();
        entries.forEach(array::add);
        return array;
    }
}
