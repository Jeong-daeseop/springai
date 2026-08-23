package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignCodeComponentMappingApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");
    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);
    private final DesignCodeComponentMappingPreviewService previewService =
            mock(DesignCodeComponentMappingPreviewService.class);
    private final DesignCodeComponentMappingHashService hashService =
            new DesignCodeComponentMappingHashService(new ObjectMapper());
    private final DesignCodeComponentMappingApprovalService service =
            new DesignCodeComponentMappingApprovalService(repository, previewService, hashService,
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 명시적확인과PreviewGate를통과한후승인Version을불변저장한다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = candidate("2.0");
        when(repository.findVersion(candidate.mappingId(), candidate.version())).thenReturn(Optional.empty());
        when(previewService.preview(any(), any(), any(), any(), any(), any()))
                .thenReturn(preview(candidate, false, true));

        DesignCodeComponentMapping approved = service.approve(
                root, null, "catalog-hash", null, candidate, "thymeleaf-krds",
                true, false, " reviewer ");

        assertThat(approved.status()).isEqualTo(DesignCodeComponentMapping.Status.APPROVED);
        assertThat(approved.approvedBy()).isEqualTo("reviewer");
        assertThat(approved.approvedAt()).isEqualTo(NOW);
        assertThat(approved.contentHash()).isEqualTo(candidate.contentHash());
        verify(repository).saveImmutable(approved);
    }

    @Test
    void 확인없음과ContentHash변조를저장전에차단한다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = candidate("2.0");
        assertThatThrownBy(() -> service.approve(
                root, null, "hash", null, candidate, "thymeleaf-krds",
                false, false, "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("명시적 확인");

        DesignCodeComponentMapping tampered = copyWithHash(candidate, "f".repeat(64));
        assertThatThrownBy(() -> service.approve(
                root, null, "hash", null, tampered, "thymeleaf-krds",
                true, false, "reviewer"))
                .isInstanceOf(DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException.class)
                .satisfies(exception -> assertThat(
                        ((DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException) exception)
                                .errorCode()).isEqualTo("MAPPING_CONTENT_HASH_MISMATCH"));
        verify(repository, never()).saveImmutable(any());
    }

    @Test
    void Fragment교차검증오류와Breaking미확인을차단한다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = candidate("2.0");
        when(repository.findVersion(candidate.mappingId(), candidate.version())).thenReturn(Optional.empty());
        when(previewService.preview(any(), any(), any(), any(), any(), any()))
                .thenReturn(preview(candidate, false, false));
        assertThatThrownBy(() -> service.approve(
                root, null, "hash", null, candidate, "thymeleaf-krds",
                true, false, "reviewer"))
                .isInstanceOf(DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException.class)
                .satisfies(exception -> assertThat(
                        ((DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException) exception)
                                .errorCode()).isEqualTo("MAPPING_PREVIEW_NOT_APPROVAL_READY"));

        when(previewService.preview(any(), any(), any(), any(), any(), any()))
                .thenReturn(preview(candidate, true, true));
        assertThatThrownBy(() -> service.approve(
                root, null, "hash", null, candidate, "thymeleaf-krds",
                true, false, "reviewer"))
                .isInstanceOf(DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException.class)
                .satisfies(exception -> assertThat(
                        ((DesignCodeComponentMappingApprovalService.MappingApprovalRejectedException) exception)
                                .errorCode()).isEqualTo("MAPPING_BREAKING_CHANGE_REQUIRES_APPROVAL"));
    }

    @Test
    void 동일Version동일Payload승인은기존결과를멱등반환한다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = candidate("2.0");
        DesignCodeComponentMapping existing = approved(candidate);
        when(repository.findVersion(candidate.mappingId(), candidate.version()))
                .thenReturn(Optional.of(existing));

        DesignCodeComponentMapping result = service.approve(
                root, null, "hash", null, candidate, "thymeleaf-krds",
                true, false, "other-reviewer");

        assertThat(result).isSameAs(existing);
        verify(previewService, never()).preview(any(), any(), any(), any(), any(), any());
        verify(repository, never()).saveImmutable(any());
    }

    @Test
    void 동일Version의다른Payload는불변Version충돌이다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = candidate("2.0");
        DesignCodeComponentMapping other = new DesignCodeComponentMapping(
                candidate.mappingId(), candidate.version(), DesignCodeComponentMapping.Status.APPROVED,
                "e".repeat(64), candidate.logicalType(), candidate.figmaComponentSetKey(),
                candidate.thymeleafFragment(), candidate.propertyMappings(), candidate.slotMappings(),
                Map.of("label", "다른 값"), candidate.supportedRendererProfiles(), candidate.sourceRevision(),
                "old", NOW.minusSeconds(60));
        when(repository.findVersion(candidate.mappingId(), candidate.version()))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.approve(
                root, null, "hash", null, candidate, "thymeleaf-krds",
                true, false, "reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAPPING_VERSION_CONFLICT");
    }

    private DesignCodeComponentMappingPreviewService.Preview preview(
            DesignCodeComponentMapping candidate, boolean breaking, boolean ready) {
        var diff = new DesignCodeComponentMappingDiffService.MappingDiff(
                "1.0", candidate.version(), false, true,
                breaking ? List.of(new DesignCodeComponentMappingDiffService.MappingChange(
                        DesignCodeComponentMappingDiffService.Area.PROPERTY, "Label",
                        DesignCodeComponentMappingDiffService.ChangeType.REMOVED,
                        List.of("all"), true)) : List.of());
        List<DesignCodeComponentMappingPreviewService.PreviewIssue> issues = ready ? List.of()
                : List.of(new DesignCodeComponentMappingPreviewService.PreviewIssue(
                        "FRAGMENT_CONTRACT", "FRAGMENT_PARAMETER_MISSING",
                        DesignCodeComponentMappingPreviewService.Severity.ERROR,
                        "Parameter 누락", "label"));
        return new DesignCodeComponentMappingPreviewService.Preview(
                null, candidate, diff,
                new DesignCodeComponentMappingCrossValidator.ValidationResult(
                        candidate.mappingId(), candidate.version(), List.of()),
                new ThymeleafFragmentContractValidator.ValidationResult(
                        candidate.mappingId(), candidate.version(), "fragment.html", "button",
                        Set.of("label"), Set.of(), Set.of(), List.of()), null, issues);
    }

    private DesignCodeComponentMapping candidate(String version) {
        DesignCodeComponentMapping unhashed = new DesignCodeComponentMapping(
                "map-button", version, DesignCodeComponentMapping.Status.REVIEW_REQUIRED,
                "0".repeat(64), "krds.button", "BUTTON_SET", "fragments/button :: button",
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Label", "label", Map.of(), true, null, null)),
                List.of(), Map.of("label", "확인"), List.of("thymeleaf-krds"),
                "revision-1", null, null);
        return copyWithHash(unhashed, hashService.compute(unhashed));
    }

    private DesignCodeComponentMapping copyWithHash(
            DesignCodeComponentMapping source, String hash) {
        return new DesignCodeComponentMapping(
                source.mappingId(), source.version(), source.status(), hash, source.logicalType(),
                source.figmaComponentSetKey(), source.thymeleafFragment(), source.propertyMappings(),
                source.slotMappings(), source.fixtureModel(), source.supportedRendererProfiles(),
                source.sourceRevision(), source.approvedBy(), source.approvedAt());
    }

    private DesignCodeComponentMapping approved(DesignCodeComponentMapping source) {
        return new DesignCodeComponentMapping(
                source.mappingId(), source.version(), DesignCodeComponentMapping.Status.APPROVED,
                source.contentHash(), source.logicalType(), source.figmaComponentSetKey(),
                source.thymeleafFragment(), source.propertyMappings(), source.slotMappings(),
                source.fixtureModel(), source.supportedRendererProfiles(), source.sourceRevision(),
                "old-reviewer", NOW.minusSeconds(60));
    }
}
