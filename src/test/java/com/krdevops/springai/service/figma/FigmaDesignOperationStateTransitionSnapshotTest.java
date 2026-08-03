package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0005(정정판): {@link FigmaDesignOperationStateService}의 상태 전이 matrix를 fixture로
 * 고정한다.
 *
 * <p>WP0 최초 baseline에서 "Figma는 상태 전이 강제가 없다"고 기록했던 건 부정확했다 —
 * {@code FigmaDesignOperation} record 자체(모델)에는 검증이 없지만, 실제 쓰기 경로인
 * {@link com.krdevops.springai.mapper.FigmaDesignOperationRepository}가 저장 직전에 이
 * {@code FigmaDesignOperationStateService}로 매번 검증한다. Thymeleaf의
 * {@code ProjectOperationStateService.transitionState}와 같은 위치(모델이 아니라 Service
 * 계층)에서 동등한 역할을 한다.
 *
 * <p>{@code APPLIED} 전이는 {@link FigmaDesignOperationStateService#assertTransitionAllowed}가
 * 아니라 {@link FigmaDesignOperationStateService#assertTransitionToAppliedAllowed}로만 검증할
 * 수 있으므로(Plugin 적용 보고 필수), 이 테스트는 {@code to == APPLIED}일 때 별도 API를 쓴다.
 */
class FigmaDesignOperationStateTransitionSnapshotTest {

    private static final Path BASELINE_PATH =
            Path.of("src/test/resources/state-machine/figma-design-operation-transitions-baseline.json");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FigmaDesignOperationStateService stateService = new FigmaDesignOperationStateService();

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
                .as("FigmaDesignOperationStatus 상태 전이 matrix가 baseline과 달라졌습니다. 의도된 변경이라면 "
                        + BASELINE_PATH + " 파일을 삭제한 뒤 이 테스트를 다시 실행해 갱신하세요.")
                .isEqualTo(stored);
    }

    @Test
    void terminalStatuses_haveNoAllowedOutgoingTransition() {
        for (FigmaDesignOperationStatus status : FigmaDesignOperationStatus.values()) {
            if (!stateService.isTerminal(status)) {
                continue;
            }
            for (FigmaDesignOperationStatus target : FigmaDesignOperationStatus.values()) {
                assertThat(isAllowed(status, target))
                        .as(status + " -> " + target + "는 최종 상태에서 허용되면 안 됩니다")
                        .isFalse();
            }
        }
    }

    @Test
    void appliedTransition_requiresPluginReport() {
        // 최소 하나의 실제 허용 경로(APPLY_REQUIRED -> APPLIED)에서 보고 없이는 거부됨을 확인한다.
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> stateService.assertTransitionToAppliedAllowed(
                        FigmaDesignOperationStatus.APPLY_REQUIRED, false));
    }

    private ArrayNode currentSnapshot() {
        ArrayNode array = objectMapper.createArrayNode();
        for (FigmaDesignOperationStatus from : FigmaDesignOperationStatus.values()) {
            for (FigmaDesignOperationStatus to : FigmaDesignOperationStatus.values()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("from", from.name());
                node.put("to", to.name());
                node.put("allowed", isAllowed(from, to));
                array.add(node);
            }
        }
        return array;
    }

    private boolean isAllowed(FigmaDesignOperationStatus from, FigmaDesignOperationStatus to) {
        try {
            if (to == FigmaDesignOperationStatus.APPLIED) {
                stateService.assertTransitionToAppliedAllowed(from, true);
            } else {
                stateService.assertTransitionAllowed(from, to);
            }
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }
}
