package com.krdevops.springai.model.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** legacy source 추적 필드의 JSON 영속성과 기존 snapshot 하위 호환을 검증한다. */
class ThymeleafOperationSnapshotSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void bindingContractAndLegacyManifestRoundTripThroughSnapshotJson() throws Exception {
        ThymeleafBindingContract contract = contract();
        LegacySourceManifest manifest = new LegacySourceManifest(List.of(
                new LegacySourceManifest.SourceFile("legacy/Controller.java", "a".repeat(64)),
                new LegacySourceManifest.SourceFile("legacy/View.jsp", "b".repeat(64))),
                "c".repeat(64));
        ThymeleafOperationSnapshot snapshot = new ThymeleafOperationSnapshot(
                1, operation(), "/tmp/project", Map.of("templates/view.html", "MISSING"),
                "design-rev", "preview-hash", manifest, contract);

        ThymeleafOperationSnapshot restored = objectMapper.readValue(
                objectMapper.writeValueAsBytes(snapshot), ThymeleafOperationSnapshot.class);

        assertThat(restored.legacySourceManifest()).isEqualTo(manifest);
        assertThat(restored.bindingContract()).isEqualTo(contract);
    }

    @Test
    void jsonWrittenBeforeLegacyTrackingLoadsWithEmptyManifest() throws Exception {
        ThymeleafOperationSnapshot current = new ThymeleafOperationSnapshot(
                1, operation(), "/tmp/project", Map.of(), "design-rev", "preview-hash");
        ObjectNode legacyJson = (ObjectNode) objectMapper.valueToTree(current);
        legacyJson.remove("legacySourceManifest");
        legacyJson.remove("bindingContract");

        ThymeleafOperationSnapshot restored = objectMapper.treeToValue(
                legacyJson, ThymeleafOperationSnapshot.class);

        assertThat(restored.legacySourceManifest()).isEqualTo(LegacySourceManifest.empty());
        assertThat(restored.bindingContract()).isNull();
    }

    private ThymeleafBindingContract contract() {
        SourceRevisionRef revision = new SourceRevisionRef("screen-1", "c".repeat(64), Instant.EPOCH);
        return new ThymeleafBindingContract(
                "screen-1", LegacyScreenRole.LIST,
                new ThymeleafRouteBinding("/users", "GET", "list", "searchVO", "UserVO",
                        false, false, List.of()),
                List.of(), List.of(), "resultList", List.of(), null,
                List.of("resultList"), List.of(), BindingContractStatus.RESOLVED,
                List.of(), revision, Instant.EPOCH);
    }

    private ThymeleafProjectOperation operation() {
        return new ThymeleafProjectOperation(
                "operation-1", "/tmp/project", ProjectOperationStatus.PREVIEW_READY,
                Map.of("templates/view.html", "<main/>"), List.of("templates/view.html"), null,
                List.of(), List.of(), false, LocalDateTime.of(2026, 8, 9, 0, 0), null);
    }
}
