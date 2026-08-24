package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.ApprovedWriteConflictGuard;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.GeneratedRegionPreservationService;
import com.krdevops.springai.service.generation.OwnershipConflictDetector;
import com.krdevops.springai.service.generation.RegionMarkerParser;
import com.krdevops.springai.service.generation.SemanticMergePlanService;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WP7 2차 pass/ARCH-0716: CRUD Pipeline 내 유일한 WRITE 어댑터. (Board/Master-Detail Orchestration
 * Service, Thymeleaf Layout 생성 등 이 Pipeline 밖의 다른 경로는 여전히
 * {@code codeService.saveGeneratedCode}를 직접 호출한다 — ARCH-0717/0718 별도 항목.)
 *
 * <p>{@code pipelineEvolutionProperties.usesV2Preview()}가 false면(명시적으로
 * {@code DISABLED}/{@code OBSERVE}/{@code DUAL_READ}로 낮춘 경우) 기존
 * {@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} 경로를 그대로 쓴다. true(모드
 * {@code V2_PREVIEW} 이상, 현재 운영 기본값 {@code V2_APPLY})면 Region Ownership 3-way 비교 + Revision drift 감지가
 * 추가된 경로를 탄다 — 상세는 {@code docs/superpowers/specs/2026-08-24-crud-generation-ownership-guard-design.md}.
 */
@Slf4j
@Component
public class CodeServiceGenerationExecutor implements GenerationExecutor {

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final CrudGenerationSnapshotStore snapshotStore;
    private final SemanticMergePlanService semanticMergePlanService;
    private final ApprovedWriteConflictGuard approvedWriteConflictGuard;

    @Autowired
    public CodeServiceGenerationExecutor(
            CodeService codeService,
            ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            CrudGenerationSnapshotStore snapshotStore,
            SemanticMergePlanService semanticMergePlanService,
            ApprovedWriteConflictGuard approvedWriteConflictGuard) {
        this.codeService = codeService;
        this.writePort = writePort;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.snapshotStore = snapshotStore;
        this.semanticMergePlanService = semanticMergePlanService;
        this.approvedWriteConflictGuard = approvedWriteConflictGuard;
    }

    /** Ownership Guard 도입 전 4-arg 호출자·테스트 호환. */
    public CodeServiceGenerationExecutor(
            CodeService codeService, ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties, CrudGenerationSnapshotStore snapshotStore) {
        this(codeService, writePort, pipelineEvolutionProperties, snapshotStore,
                new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService()),
                new ApprovedWriteConflictGuard());
    }

    /** Ownership Guard 도입 전 2-arg 호출자·테스트 호환 — usesV2Preview()가 항상 false이므로 legacy 경로만 탄다. */
    public CodeServiceGenerationExecutor(CodeService codeService, ApprovedProjectWritePort writePort) {
        this(codeService, writePort, new PipelineEvolutionProperties(), null);
    }

    @Override
    public GenerationExecution execute(RenderedGenerationPlan plan) {
        if (!pipelineEvolutionProperties.usesV2Preview()) {
            return legacyExecute(plan);
        }
        return ownershipAwareExecute(plan);
    }

    /** 지금까지의 BEST_EFFORT_COMPATIBILITY 경로 — 원문 그대로 옮겨왔다. */
    private GenerationExecution legacyExecute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> toApply = plan.files().stream().filter(RenderedFilePlan::rendered).toList();

        Path outputRoot = null;
        Map<String, String> failureMessagesByRelative = Map.of();
        if (!toApply.isEmpty()) {
            String outputPath = plan.context().outputPath();
            codeService.validateOutputRoot(outputPath);
            outputRoot = Path.of(outputPath);

            List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
            for (RenderedFilePlan file : toApply) {
                String relative = outputRoot.relativize(file.targetPath()).toString();
                changes.add(new ProjectChangeSet.FileChange(relative, null, file.source(), null));
            }

            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath, null, changes, List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            failureMessagesByRelative = writePort.apply(changeSet).failureMessages();
        }

        List<RenderedFilePlan> succeeded = new ArrayList<>();
        List<GenerationFailure> failed = new ArrayList<>();
        for (RenderedFilePlan file : plan.files()) {
            if (!file.rendered()) {
                failed.add(file.renderFailure());
                continue;
            }
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String failureMessage = failureMessagesByRelative.get(relative);
            if (failureMessage != null) {
                failed.add(new GenerationFailure(
                        file.layerKey(), file.displayName() + " — 파일 저장 실패: " + failureMessage));
                log.error("[pipeline] 저장 실패: {}", file.targetPath());
            } else {
                succeeded.add(file);
                log.info("[pipeline] 저장 완료: {}", file.targetPath());
            }
        }

        return new GenerationExecution(plan, succeeded, failed);
    }

    /**
     * Region Ownership 3-way 비교 + ATOMIC_APPROVED Revision drift 감지를 적용한 Apply 경로.
     * 상세는 {@code docs/superpowers/specs/2026-08-24-crud-generation-ownership-guard-design.md}.
     */
    private GenerationExecution ownershipAwareExecute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> toApply = plan.files().stream().filter(RenderedFilePlan::rendered).toList();
        List<GenerationFailure> renderFailures = plan.files().stream()
                .filter(file -> !file.rendered()).map(RenderedFilePlan::renderFailure).toList();
        if (toApply.isEmpty()) {
            return new GenerationExecution(plan, List.of(), renderFailures);
        }

        String outputPath = plan.context().outputPath();
        codeService.validateOutputRoot(outputPath);
        Path outputRoot = Path.of(outputPath);

        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputPath, plan.context().tableName(), plan.context().viewType());
        GenerationOwnershipManifest base = snapshotStore.findLatest(operationId).orElse(null);

        Map<String, String> currentContentByPath = new LinkedHashMap<>();
        Map<String, String> currentHashByPath = new LinkedHashMap<>();
        Map<String, List<RegionMarkerParser.ParsedRegion>> newRegionsByPath = new LinkedHashMap<>();
        List<ThreeWayRegionComparison> allComparisons = new ArrayList<>();
        Map<String, GenerationOwnershipManifest.RegionType> regionTypes = new LinkedHashMap<>();

        for (RenderedFilePlan file : toApply) {
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String current = readIfExists(file.targetPath());
            currentContentByPath.put(relative, current);
            currentHashByPath.put(relative, current == null ? "MISSING" : sha256(current));

            List<RegionMarkerParser.ParsedRegion> newRegions = RegionMarkerParser.parse(file.source());
            List<RegionMarkerParser.ParsedRegion> currentRegions = RegionMarkerParser.parse(current);
            newRegionsByPath.put(relative, newRegions);
            List<GenerationOwnershipManifest.Region> baseRegions =
                    base == null ? List.of() : base.regionsFor(relative);

            Set<String> regionIds = new LinkedHashSet<>();
            newRegions.forEach(region -> regionIds.add(region.regionId()));
            currentRegions.forEach(region -> regionIds.add(region.regionId()));
            baseRegions.forEach(region -> regionIds.add(region.regionId()));

            for (String regionId : regionIds) {
                var baseRegion = baseRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);
                var newRegion = newRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);
                var currentRegion = currentRegions.stream()
                        .filter(region -> region.regionId().equals(regionId)).findFirst().orElse(null);

                String baseHash = baseRegion == null ? null : baseRegion.contentHash();
                String newHash = newRegion == null ? null : RegionMarkerParser.hashOf(newRegion.content());
                String currentHash = currentRegion == null ? null : RegionMarkerParser.hashOf(currentRegion.content());
                GenerationOwnershipManifest.RegionType type = newRegion != null ? newRegion.regionType()
                        : (baseRegion != null ? baseRegion.regionType() : GenerationOwnershipManifest.RegionType.UNKNOWN);

                String comparisonId = relative + "::" + regionId;
                ThreeWayRegionComparison comparison =
                        ThreeWayRegionComparison.compare(comparisonId, baseHash, currentHash, newHash);
                boolean protectedRegionVanished = newRegion == null && baseRegion != null
                        && (type == GenerationOwnershipManifest.RegionType.PROTECTED
                            || type == GenerationOwnershipManifest.RegionType.BINDING)
                        && comparison.status() != ThreeWayRegionComparison.ChangeStatus.BOTH_CHANGED;
                // 마커가 깨져(짝 안 맞음·id 중복) RegionMarkerParser가 파일 전체를 UNKNOWN으로
                // 강등한 경우, 3-way 비교 결과가 자연스럽게 CURRENT_ONLY/NEW_ONLY로 나오더라도
                // 안전하게 판단할 수 없으므로 강제로 BOTH_CHANGED로 승격해 사람 검토를 요구한다.
                boolean unknownRegion = type == GenerationOwnershipManifest.RegionType.UNKNOWN
                        && comparison.status() != ThreeWayRegionComparison.ChangeStatus.UNCHANGED;
                if (protectedRegionVanished || unknownRegion) {
                    comparison = new ThreeWayRegionComparison(comparisonId, baseHash, currentHash, newHash,
                            ThreeWayRegionComparison.ChangeStatus.BOTH_CHANGED);
                }
                regionTypes.put(comparisonId, type);
                allComparisons.add(comparison);
            }
        }

        var mergePlan = semanticMergePlanService.preview(allComparisons, regionTypes);
        try {
            approvedWriteConflictGuard.requireApplyAllowed(mergePlan);
        } catch (ApprovedWriteConflictGuard.ApplyConflictBlockedException conflict) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("ownership-guard",
                    "Region 소유권 충돌로 Apply 중단: " + conflict.plan().conflictRegionIds()));
            return new GenerationExecution(plan, List.of(), failures);
        }

        // SemanticMergePlan.preservedRegionIds()는 이미 GeneratedRegionPreservationService.plan()이
        // 계산한 결과이므로, Executor가 그 서비스를 따로 다시 호출할 필요가 없다.
        Set<String> preservedComparisonIds = new LinkedHashSet<>(mergePlan.preservedRegionIds());

        Set<String> splicedComparisonIds = new LinkedHashSet<>();
        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        Map<String, List<RegionMarkerParser.ParsedRegion>> finalRegionsByPath = new LinkedHashMap<>();
        // 실제 쓰기 대상은 여기서 정해지는 toApply뿐이다 — Planner→Renderer→Executor 사이에 다른
        // 파일이 끼어드는 단계가 없으므로, scopeManifest 없이도 이 목록 자체가 이번 호출의 Scope다.
        for (RenderedFilePlan file : toApply) {
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String spliced = spliceRegions(file.source(), newRegionsByPath.get(relative),
                    relative, currentContentByPath.get(relative), preservedComparisonIds,
                    splicedComparisonIds);
            finalRegionsByPath.put(relative, RegionMarkerParser.parse(spliced));
            changes.add(new ProjectChangeSet.FileChange(relative, currentHashByPath.get(relative),
                    spliced, sha256(spliced)));
        }

        Set<String> unresolvedPreserved = new LinkedHashSet<>(preservedComparisonIds);
        unresolvedPreserved.removeAll(splicedComparisonIds);
        if (!unresolvedPreserved.isEmpty()) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("ownership-guard",
                    "보존 대상 Region을 실제로 복원할 수 없어 Apply 중단(마커 손상 가능성): "
                            + unresolvedPreserved));
            return new GenerationExecution(plan, List.of(), failures);
        }

        createDirectoriesIfMissing(outputRoot);
        ApplyOutcome outcome = writePort.apply(new ProjectChangeSet(
                outputPath, null, changes, List.of(), ProjectWritePolicy.ATOMIC_APPROVED));

        if (outcome.status() == ApplyOutcome.Status.CONFLICT) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("write-guard",
                    "동시 수정으로 파일 Revision이 어긋나 Apply 중단: " + outcome.conflictingPaths()));
            return new GenerationExecution(plan, List.of(), failures);
        }
        if (outcome.status() != ApplyOutcome.Status.APPLIED) {
            List<GenerationFailure> failures = new ArrayList<>(renderFailures);
            failures.add(new GenerationFailure("write-guard",
                    "Apply 실패(" + outcome.status() + "): " + outcome.failureDetail()));
            return new GenerationExecution(plan, List.of(), failures);
        }

        snapshotStore.save(operationId, buildOwnershipManifest(operationId, finalRegionsByPath));
        return new GenerationExecution(plan, toApply, renderFailures);
    }

    /** PRESERVE 대상 Region만 New 콘텐츠에서 Current 내용으로 치환한다. 실제로 치환에 성공한
     * comparisonId를 splicedComparisonIds에 기록한다 — 호출자가 "보존하기로 했는데 실제로는
     * 못 한" 케이스를 감지할 수 있게 한다. */
    private String spliceRegions(String newContent, List<RegionMarkerParser.ParsedRegion> newRegions,
            String relative, String currentContent, Set<String> preservedComparisonIds,
            Set<String> splicedComparisonIds) {
        if (currentContent == null) {
            return newContent;
        }
        List<RegionMarkerParser.ParsedRegion> currentRegions = RegionMarkerParser.parse(currentContent);
        StringBuilder result = new StringBuilder(newContent);
        // 뒤에서부터 치환해야 앞쪽 오프셋이 밀리지 않는다.
        for (int i = newRegions.size() - 1; i >= 0; i--) {
            RegionMarkerParser.ParsedRegion region = newRegions.get(i);
            String comparisonId = relative + "::" + region.regionId();
            if (!preservedComparisonIds.contains(comparisonId)) {
                continue;
            }
            currentRegions.stream()
                    .filter(current -> current.regionId().equals(region.regionId()))
                    .findFirst()
                    .ifPresent(currentRegion -> {
                        result.replace(region.startIndex(), region.endIndex(), currentRegion.content());
                        splicedComparisonIds.add(comparisonId);
                    });
        }
        return result.toString();
    }

    private GenerationOwnershipManifest buildOwnershipManifest(
            String operationId, Map<String, List<RegionMarkerParser.ParsedRegion>> regionsByPath) {
        List<GenerationOwnershipManifest.ArtifactOwnership> artifacts = new ArrayList<>();
        for (var entry : regionsByPath.entrySet()) {
            List<GenerationOwnershipManifest.Region> regions = entry.getValue().stream()
                    .map(region -> new GenerationOwnershipManifest.Region(
                            region.regionId(), region.regionType(), RegionMarkerParser.hashOf(region.content())))
                    .toList();
            artifacts.add(new GenerationOwnershipManifest.ArtifactOwnership(
                    entry.getKey(), regions, GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai"));
        }
        return GenerationOwnershipManifest.builder(operationId).artifacts(artifacts).build();
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("기존 파일 읽기 실패: " + path, exception);
        }
    }

    private void createDirectoriesIfMissing(Path root) {
        if (Files.exists(root)) {
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("출력 루트 생성 실패: " + root, exception);
        }
    }

    private String sha256(String text) {
        return ContentHashes.sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }
}
