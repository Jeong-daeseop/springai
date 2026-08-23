package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 현재 승인 Mapping과 후보 Diff 및 모든 승인 전 검증 Evidence를 읽기 전용으로 조합한다. */
@Service
public class DesignCodeComponentMappingPreviewService {

    private final DesignCodeComponentMappingRepository repository;
    private final DesignCodeComponentMappingDiffService diffService;
    private final DesignCodeComponentMappingCrossValidator crossValidator;
    private final ThymeleafFragmentContractValidator fragmentValidator;
    private final ComponentFixtureValidationService fixtureValidator;

    public DesignCodeComponentMappingPreviewService(
            DesignCodeComponentMappingRepository repository,
            DesignCodeComponentMappingDiffService diffService,
            DesignCodeComponentMappingCrossValidator crossValidator,
            ThymeleafFragmentContractValidator fragmentValidator,
            ComponentFixtureValidationService fixtureValidator) {
        this.repository = repository;
        this.diffService = diffService;
        this.crossValidator = crossValidator;
        this.fragmentValidator = fragmentValidator;
        this.fixtureValidator = fixtureValidator;
    }

    public Preview preview(
            Path projectRoot,
            ComponentCatalog catalog,
            String catalogHash,
            ComponentRegistrySnapshotV3 registry,
            DesignCodeComponentMapping candidate,
            String rendererProfile) {
        if (candidate == null) throw new IllegalArgumentException("candidate Mapping은 필수입니다.");
        DesignCodeComponentMapping base = repository.findLatestApproved(candidate.logicalType()).orElse(null);
        DesignCodeComponentMappingDiffService.MappingDiff diff = diffService.compare(base, candidate);
        DesignCodeComponentMappingCrossValidator.ValidationResult cross = crossValidator.validate(
                catalog, catalogHash, registry, candidate, rendererProfile);
        ThymeleafFragmentContractValidator.ValidationResult fragment =
                fragmentValidator.validate(projectRoot, candidate);
        ComponentFixtureValidationService.ValidationResult fixture =
                fixtureValidator.validate(projectRoot, candidate);
        List<PreviewIssue> issues = new ArrayList<>();
        cross.issues().forEach(issue -> issues.add(fromCross(issue)));
        fragment.issues().forEach(issue -> issues.add(fromFragment(issue)));
        fixture.issues().stream()
                .filter(issue -> !"FRAGMENT_CONTRACT".equals(issue.source()))
                .forEach(issue -> issues.add(new PreviewIssue(
                        "FIXTURE_VALIDATION", issue.code(),
                        issue.severity() == ComponentFixtureValidationService.Severity.ERROR
                                ? Severity.ERROR : Severity.WARNING,
                        issue.message(), issue.target())));
        if (!diff.initialCreation() && !diff.contentChanged() && diff.changes().isEmpty()) {
            issues.add(new PreviewIssue("DIFF", "NO_MAPPING_CHANGES", Severity.WARNING,
                    "현재 승인 Mapping과 후보의 내용 변경이 없습니다.", candidate.mappingId()));
        }
        return new Preview(base, candidate, diff, cross, fragment, fixture, issues);
    }

    private PreviewIssue fromCross(DesignSystemIssue issue) {
        Severity severity = issue.severity() == DesignSystemIssue.Severity.WARNING
                ? Severity.WARNING : Severity.ERROR;
        return new PreviewIssue("CROSS_CONTRACT", issue.code(), severity,
                issue.message(), issue.targetId());
    }

    private PreviewIssue fromFragment(ThymeleafFragmentContractValidator.ValidationIssue issue) {
        Severity severity = issue.severity() == ThymeleafFragmentContractValidator.Severity.WARNING
                ? Severity.WARNING : Severity.ERROR;
        return new PreviewIssue("FRAGMENT_CONTRACT", issue.code(), severity,
                issue.message(), issue.target());
    }

    public record Preview(
            DesignCodeComponentMapping base,
            DesignCodeComponentMapping candidate,
            DesignCodeComponentMappingDiffService.MappingDiff diff,
            DesignCodeComponentMappingCrossValidator.ValidationResult crossValidation,
            ThymeleafFragmentContractValidator.ValidationResult fragmentValidation,
            ComponentFixtureValidationService.ValidationResult fixtureValidation,
            List<PreviewIssue> issues
    ) {
        public Preview {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }

        public boolean approvalReady() {
            return valid() && (diff.initialCreation() || diff.contentChanged() || !diff.changes().isEmpty());
        }
    }

    public record PreviewIssue(
            String source,
            String code,
            Severity severity,
            String message,
            String target
    ) {}

    public enum Severity { WARNING, ERROR }
}
