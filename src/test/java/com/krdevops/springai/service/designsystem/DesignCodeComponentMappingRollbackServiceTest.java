package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignCodeComponentMappingRollbackServiceTest {

    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);
    private final DesignCodeComponentMappingApprovalService approvalService =
            mock(DesignCodeComponentMappingApprovalService.class);
    private final DesignCodeComponentMappingHashService hashService =
            new DesignCodeComponentMappingHashService(new ObjectMapper());
    private final DesignCodeComponentMappingRollbackService service =
            new DesignCodeComponentMappingRollbackService(repository, approvalService, hashService);

    @Test
    void 과거승인Payload를새Version후보로복원하고동일승인Gate에위임한다(@TempDir Path root) {
        DesignCodeComponentMapping target = approved("1.0");
        when(repository.findVersion("map-button", "1.0")).thenReturn(Optional.of(target));
        when(repository.findVersion("map-button", "3.0")).thenReturn(Optional.empty());
        when(approvalService.approve(any(), any(), any(), any(), any(), any(),
                eq(true), eq(true), eq("operator")))
                .thenAnswer(invocation -> approve(invocation.getArgument(4)));

        DesignCodeComponentMapping result = service.rollback(
                root, null, "catalog-hash", null, "map-button", "1.0", "3.0",
                "thymeleaf-krds", true, true, "operator");

        ArgumentCaptor<DesignCodeComponentMapping> candidate =
                ArgumentCaptor.forClass(DesignCodeComponentMapping.class);
        verify(approvalService).approve(eq(root), any(), eq("catalog-hash"), any(),
                candidate.capture(), eq("thymeleaf-krds"), eq(true), eq(true), eq("operator"));
        assertThat(candidate.getValue().version()).isEqualTo("3.0");
        assertThat(candidate.getValue().status())
                .isEqualTo(DesignCodeComponentMapping.Status.REVIEW_REQUIRED);
        assertThat(candidate.getValue().fixtureModel()).isEqualTo(target.fixtureModel());
        assertThat(candidate.getValue().propertyMappings()).isEqualTo(target.propertyMappings());
        assertThat(candidate.getValue().contentHash()).isEqualTo(target.contentHash());
        assertThat(result.status()).isEqualTo(DesignCodeComponentMapping.Status.APPROVED);
    }

    @Test
    void 확인되지않았거나승인되지않은Target은Rollback할수없다(@TempDir Path root) {
        assertThatThrownBy(() -> service.rollback(
                root, null, "hash", null, "map-button", "1.0", "3.0",
                "thymeleaf-krds", false, false, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("명시적 확인");

        DesignCodeComponentMapping draft = new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.DRAFT,
                "a".repeat(64), "krds.button", "BUTTON_SET", "fragments/button :: button",
                List.of(), List.of(), null, List.of("thymeleaf-krds"), "revision-1", null, null);
        when(repository.findVersion("map-button", "1.0")).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.rollback(
                root, null, "hash", null, "map-button", "1.0", "3.0",
                "thymeleaf-krds", true, false, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLLBACK_TARGET_NOT_APPROVED");
    }

    @Test
    void 과거Version덮어쓰기와이미존재하는새Version을거부한다(@TempDir Path root) {
        DesignCodeComponentMapping target = approved("1.0");
        when(repository.findVersion("map-button", "1.0")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.rollback(
                root, null, "hash", null, "map-button", "1.0", "1.0",
                "thymeleaf-krds", true, false, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("새 Version");

        when(repository.findVersion("map-button", "3.0")).thenReturn(Optional.of(approved("3.0")));
        assertThatThrownBy(() -> service.rollback(
                root, null, "hash", null, "map-button", "1.0", "3.0",
                "thymeleaf-krds", true, false, "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAPPING_VERSION_CONFLICT");
    }

    private DesignCodeComponentMapping approved(String version) {
        DesignCodeComponentMapping unhashed = new DesignCodeComponentMapping(
                "map-button", version, DesignCodeComponentMapping.Status.APPROVED,
                "0".repeat(64), "krds.button", "BUTTON_SET", "fragments/button :: button",
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Label", "label", Map.of(), true, null, null)),
                List.of(), Map.of("label", "과거 Fixture"), List.of("thymeleaf-krds"),
                "revision-1", "reviewer", Instant.parse("2026-08-20T01:00:00Z"));
        return new DesignCodeComponentMapping(
                unhashed.mappingId(), unhashed.version(), unhashed.status(), hashService.compute(unhashed),
                unhashed.logicalType(), unhashed.figmaComponentSetKey(), unhashed.thymeleafFragment(),
                unhashed.propertyMappings(), unhashed.slotMappings(), unhashed.fixtureModel(),
                unhashed.supportedRendererProfiles(), unhashed.sourceRevision(),
                unhashed.approvedBy(), unhashed.approvedAt());
    }

    private DesignCodeComponentMapping approve(DesignCodeComponentMapping candidate) {
        return new DesignCodeComponentMapping(
                candidate.mappingId(), candidate.version(), DesignCodeComponentMapping.Status.APPROVED,
                candidate.contentHash(), candidate.logicalType(), candidate.figmaComponentSetKey(),
                candidate.thymeleafFragment(), candidate.propertyMappings(), candidate.slotMappings(),
                candidate.fixtureModel(), candidate.supportedRendererProfiles(), candidate.sourceRevision(),
                "operator", Instant.parse("2026-08-23T03:00:00Z"));
    }
}
