package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignCodeComponentMappingPreviewServiceTest {

    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);
    private final DesignCodeComponentMappingCrossValidator crossValidator =
            mock(DesignCodeComponentMappingCrossValidator.class);
    private final ThymeleafFragmentContractValidator fragmentValidator =
            mock(ThymeleafFragmentContractValidator.class);
    private final ComponentFixtureValidationService fixtureValidator =
            mock(ComponentFixtureValidationService.class);
    private final DesignCodeComponentMappingPreviewService service =
            new DesignCodeComponentMappingPreviewService(repository,
                    new DesignCodeComponentMappingDiffService(), crossValidator, fragmentValidator,
                    fixtureValidator);

    @Test
    void 현재승인버전을조회해Diff와세검증Evidence를결합한다(@TempDir Path root) {
        DesignCodeComponentMapping base = mapping("1.0", "a".repeat(64),
                DesignCodeComponentMapping.Status.APPROVED);
        DesignCodeComponentMapping candidate = mapping("2.0", "b".repeat(64),
                DesignCodeComponentMapping.Status.REVIEW_REQUIRED);
        when(repository.findLatestApproved("krds.button")).thenReturn(Optional.of(base));
        when(crossValidator.validate(any(), any(), any(), any(), anyString()))
                .thenReturn(new DesignCodeComponentMappingCrossValidator.ValidationResult(
                        candidate.mappingId(), candidate.version(), List.of()));
        when(fragmentValidator.validate(root, candidate)).thenReturn(fragment(candidate, List.of()));
        when(fixtureValidator.validate(root, candidate)).thenReturn(fixture(candidate, List.of()));

        DesignCodeComponentMappingPreviewService.Preview preview = service.preview(
                root, null, "catalog-hash", null, candidate, "thymeleaf-krds");

        assertThat(preview.valid()).isTrue();
        assertThat(preview.approvalReady()).isTrue();
        assertThat(preview.base()).isEqualTo(base);
        assertThat(preview.diff().baseVersion()).isEqualTo("1.0");
        verify(repository).findLatestApproved("krds.button");
    }

    @Test
    void 검증오류를출처와함께합치고승인준비를차단한다(@TempDir Path root) {
        DesignCodeComponentMapping candidate = mapping("2.0", "b".repeat(64),
                DesignCodeComponentMapping.Status.REVIEW_REQUIRED);
        when(repository.findLatestApproved("krds.button")).thenReturn(Optional.empty());
        when(crossValidator.validate(any(), any(), any(), any(), anyString()))
                .thenReturn(new DesignCodeComponentMappingCrossValidator.ValidationResult(
                        candidate.mappingId(), candidate.version(), List.of(new DesignSystemIssue(
                                "MAPPING_FIGMA_KEY_MISMATCH", DesignSystemIssue.Severity.ERROR,
                                "키 불일치", "krds.button"))));
        when(fragmentValidator.validate(root, candidate)).thenReturn(fragment(candidate, List.of(
                new ThymeleafFragmentContractValidator.ValidationIssue(
                        "FRAGMENT_PARAMETER_MISSING",
                        ThymeleafFragmentContractValidator.Severity.ERROR,
                        "Parameter 누락", "label"))));
        when(fixtureValidator.validate(root, candidate)).thenReturn(fixture(candidate, List.of(
                new ComponentFixtureValidationService.FixtureIssue(
                        "PROPERTY_RESOLUTION", "REQUIRED_PROPERTY_MISSING",
                        ComponentFixtureValidationService.Severity.ERROR,
                        "필수 Fixture 누락", "Label"))));

        DesignCodeComponentMappingPreviewService.Preview preview = service.preview(
                root, null, "catalog-hash", null, candidate, "thymeleaf-krds");

        assertThat(preview.valid()).isFalse();
        assertThat(preview.approvalReady()).isFalse();
        assertThat(preview.issues()).extracting(
                issue -> issue.source() + ":" + issue.code())
                .containsExactly(
                        "CROSS_CONTRACT:MAPPING_FIGMA_KEY_MISMATCH",
                        "FRAGMENT_CONTRACT:FRAGMENT_PARAMETER_MISSING",
                        "FIXTURE_VALIDATION:REQUIRED_PROPERTY_MISSING");
    }

    @Test
    void 승인버전과동일한후보는변경없음경고와함께승인준비가아니다(@TempDir Path root) {
        DesignCodeComponentMapping same = mapping("1.0", "a".repeat(64),
                DesignCodeComponentMapping.Status.APPROVED);
        when(repository.findLatestApproved("krds.button")).thenReturn(Optional.of(same));
        when(crossValidator.validate(any(), any(), any(), any(), anyString()))
                .thenReturn(new DesignCodeComponentMappingCrossValidator.ValidationResult(
                        same.mappingId(), same.version(), List.of()));
        when(fragmentValidator.validate(root, same)).thenReturn(fragment(same, List.of()));
        when(fixtureValidator.validate(root, same)).thenReturn(fixture(same, List.of()));

        DesignCodeComponentMappingPreviewService.Preview preview = service.preview(
                root, null, "catalog-hash", null, same, "thymeleaf-krds");

        assertThat(preview.valid()).isTrue();
        assertThat(preview.approvalReady()).isFalse();
        assertThat(preview.issues()).extracting(DesignCodeComponentMappingPreviewService.PreviewIssue::code)
                .containsExactly("NO_MAPPING_CHANGES");
    }

    private ThymeleafFragmentContractValidator.ValidationResult fragment(
            DesignCodeComponentMapping mapping,
            List<ThymeleafFragmentContractValidator.ValidationIssue> issues) {
        return new ThymeleafFragmentContractValidator.ValidationResult(
                mapping.mappingId(), mapping.version(), "templates/fragments/button.html", "button",
                Set.of("label"), Set.of(), Set.of(), issues);
    }

    private ComponentFixtureValidationService.ValidationResult fixture(
            DesignCodeComponentMapping mapping,
            List<ComponentFixtureValidationService.FixtureIssue> issues) {
        return new ComponentFixtureValidationService.ValidationResult(
                mapping.mappingId(), mapping.version(), null, false,
                "a".repeat(64), "b".repeat(64), Map.of(), fragment(mapping, List.of()), issues);
    }

    private DesignCodeComponentMapping mapping(
            String version, String hash, DesignCodeComponentMapping.Status status) {
        String approvedBy = status == DesignCodeComponentMapping.Status.APPROVED ? "reviewer" : null;
        Instant approvedAt = status == DesignCodeComponentMapping.Status.APPROVED
                ? Instant.parse("2026-08-23T01:00:00Z") : null;
        return new DesignCodeComponentMapping(
                "map-button", version, status, hash, "krds.button", "BUTTON_SET",
                "fragments/button :: button",
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Label", "label", Map.of(), true, null, null)),
                List.of(), null, List.of("thymeleaf-krds"), "revision-1", approvedBy, approvedAt);
    }
}
