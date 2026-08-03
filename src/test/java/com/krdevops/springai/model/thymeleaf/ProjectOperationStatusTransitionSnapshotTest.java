package com.krdevops.springai.model.thymeleaf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0005: Thymeleaf {@link ProjectOperationStatus} 상태 전이 matrix를 fixture로 고정한다.
 *
 * <p>{@code ThymeleafProjectOperation.canTransitionTo}가 실제로 허용하는 전이를 전수(9×9=81)
 * 조사해 저장한다 — 코드가 바뀌어 어떤 전이가 조용히 허용/차단되면 이 테스트가 즉시 잡는다.
 *
 * <p><b>Figma 쪽은 이 fixture에 포함하지 않는다.</b> {@code FigmaDesignOperation}은
 * {@code withNextRevision}으로 임의의 다음 상태를 받아들일 뿐 {@code canTransitionTo} 같은
 * 컴파일 타임 전이 검증이 없다 — 즉 Figma와 Thymeleaf는 "공통 상태 전이 정책"을 공유하지 않는
 * 서로 다른 상태 모델이라는 사실 자체가 이 기준선의 관찰 결과다(WP4 ARCH-0404 "기능 공통 상태와
 * Figma/Thymeleaf 확장 상태를 구분한다"의 선행 근거).
 */
class ProjectOperationStatusTransitionSnapshotTest {

    private static final Path BASELINE_PATH =
            Path.of("src/test/resources/state-machine/thymeleaf-project-operation-transitions-baseline.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transitions_matchStoredBaseline() throws IOException {
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
                .as("ProjectOperationStatus 상태 전이 matrix가 baseline과 달라졌습니다. 의도된 변경이라면 "
                        + BASELINE_PATH + " 파일을 삭제한 뒤 이 테스트를 다시 실행해 갱신하세요.")
                .isEqualTo(stored);
    }

    @Test
    void terminalStatuses_haveNoAllowedOutgoingTransition() {
        for (ProjectOperationStatus status : List.of(
                ProjectOperationStatus.VALIDATED,
                ProjectOperationStatus.FAILED,
                ProjectOperationStatus.CONFLICT,
                ProjectOperationStatus.REJECTED)) {
            ThymeleafProjectOperation operation = operationWithStatus(status);
            for (ProjectOperationStatus target : ProjectOperationStatus.values()) {
                assertThat(operation.canTransitionTo(target))
                        .as(status + " -> " + target + "는 최종 상태에서 허용되면 안 됩니다")
                        .isFalse();
            }
        }
    }

    private ArrayNode currentSnapshot() {
        ArrayNode array = objectMapper.createArrayNode();
        for (ProjectOperationStatus from : ProjectOperationStatus.values()) {
            ThymeleafProjectOperation operation = operationWithStatus(from);
            for (ProjectOperationStatus to : ProjectOperationStatus.values()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("from", from.name());
                node.put("to", to.name());
                node.put("allowed", operation.canTransitionTo(to));
                array.add(node);
            }
        }
        return array;
    }

    private ThymeleafProjectOperation operationWithStatus(ProjectOperationStatus status) {
        return new ThymeleafProjectOperation(
                "op-1", "/project", status, Map.of(), List.of(), null, List.of(), List.of(), true,
                LocalDateTime.now(), null);
    }
}
