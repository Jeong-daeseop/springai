package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.mapper.ThymeleafConversionOperationRepository;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.service.ThymeleafRenderValidator;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.contract.OperationHashFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * I-5B/C: I-2(Binding Contract)~I-4(Skeleton/Bound View/Render)를 하나의 Project Operation으로
 * 엮어 ANALYZED→CONTRACT_READY→PREVIEW_READY까지 진행하고({@link #analyzeAndPreview}), 사람 승인
 * 후({@link #approve}) 실제 대상 프로젝트에 안전하게 적용한다({@link #apply}).
 *
 * <p>승인 전에는 어떤 파일도 건드리지 않는다 — {@link #analyzeAndPreview}/{@link #approve}는
 * {@link LegacySourceInventoryService}의 쓰기 메서드를 호출하지 않고, {@link #apply}만 호출한다.
 */
@Service
public class ThymeleafConversionOrchestrationService {

    private static final String STAGE_PREVIEW = "PREVIEW";
    private static final String STAGE_APPLY = "APPLY";

    private final LegacyBindingContractAssembler assembler;
    private final ThymeleafSkeletonPlanner skeletonPlanner;
    private final LegacyThymeleafViewComposer viewComposer;
    private final LegacyThymeleafRenderer renderer;
    private final ThymeleafConversionOperationRepository repository;
    private final LegacySourceInventoryService inventoryService;
    private final ThymeleafRenderValidator renderValidator;
    private final OperationHashFactory operationHashFactory;
    private final GenerationIssueFactory issueFactory;

    public ThymeleafConversionOrchestrationService(
            LegacyBindingContractAssembler assembler,
            ThymeleafSkeletonPlanner skeletonPlanner,
            LegacyThymeleafViewComposer viewComposer,
            LegacyThymeleafRenderer renderer,
            ThymeleafConversionOperationRepository repository,
            LegacySourceInventoryService inventoryService,
            ThymeleafRenderValidator renderValidator,
            OperationHashFactory operationHashFactory,
            GenerationIssueFactory issueFactory
    ) {
        this.assembler = assembler;
        this.skeletonPlanner = skeletonPlanner;
        this.viewComposer = viewComposer;
        this.renderer = renderer;
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.renderValidator = renderValidator;
        this.operationHashFactory = operationHashFactory;
        this.issueFactory = issueFactory;
    }

    /** 멱등: 같은 화면 계약+targetRelativePath로 재호출하면 기존 Operation을 그대로 반환한다. */
    public ThymeleafGenerationStageResult<ThymeleafConversionOperation> analyzeAndPreview(
            LegacyScreenAnalysis analysis, String pageTitle, String targetRelativePath) {
        ThymeleafGenerationStageResult<ThymeleafBindingContract> contractResult = assembler.assemble(analysis);
        if (!contractResult.successful()) {
            return ThymeleafGenerationStageResult.failure(contractResult.issues());
        }
        ThymeleafBindingContract contract = contractResult.value();

        ThymeleafConversionOperation operation = repository.createOrReuse(contract, targetRelativePath);
        if (operation.status() != ThymeleafConversionOperationStatus.ANALYZED) {
            return ThymeleafGenerationStageResult.success(operation, operation.issues());
        }

        ThymeleafConversionOperation contractReady = repository.appendTransition(
                operation.operationId(), ThymeleafConversionOperationStatus.CONTRACT_READY,
                contract, null, null, contract.issues(), List.of());

        ThymeleafSkeleton skeleton = skeletonPlanner.plan(analysis.screenId(), analysis.screenRole(), pageTitle);
        ThymeleafGenerationStageResult<BoundThymeleafView> viewResult = viewComposer.compose(skeleton, contract);
        if (!viewResult.successful()) {
            repository.appendTransition(contractReady.operationId(), ThymeleafConversionOperationStatus.FAILED,
                    null, null, null, viewResult.issues(), List.of());
            return ThymeleafGenerationStageResult.failure(viewResult.issues());
        }

        String html;
        try {
            html = renderer.render(viewResult.value());
        } catch (RuntimeException exception) {
            List<GenerationIssue> issues = List.of(fatal(
                    "THYMELEAF_RENDER_FAILED", STAGE_PREVIEW, analysis.screenId(), exception.getMessage()));
            repository.appendTransition(contractReady.operationId(), ThymeleafConversionOperationStatus.FAILED,
                    null, null, null, issues, List.of());
            return ThymeleafGenerationStageResult.failure(issues);
        }

        ThymeleafRenderValidator.RenderReport report = validateHtmlContent(html);
        if (!report.passed()) {
            List<GenerationIssue> issues = List.of(fatal("THYMELEAF_PARSE_FAILED", STAGE_PREVIEW,
                    analysis.screenId(), String.join("; ", report.failures())));
            repository.appendTransition(contractReady.operationId(), ThymeleafConversionOperationStatus.FAILED,
                    null, null, null, issues, List.of());
            return ThymeleafGenerationStageResult.failure(issues);
        }

        ThymeleafConversionOperation previewReady = repository.appendTransition(
                contractReady.operationId(), ThymeleafConversionOperationStatus.PREVIEW_READY,
                null, html, null, List.of(), List.of());
        return ThymeleafGenerationStageResult.success(previewReady, previewReady.issues());
    }

    /** 사람이 Preview를 확인한 뒤에만 호출해야 한다 — 이 메서드 자체는 파일을 쓰지 않는다. */
    public ThymeleafConversionOperation approve(String operationId) {
        return repository.appendTransition(operationId, ThymeleafConversionOperationStatus.APPROVED,
                null, null, null, List.of(), List.of());
    }

    /**
     * 승인된 Operation을 실제 프로젝트에 적용한다. {@code currentAnalysis}는 호출자가 Apply 직전에
     * 다시 읽은 JSP/Controller/VO 분석 결과다 — 그 {@code sourceRevision}이 분석 시점과 다르면
     * 파일을 건드리지 않고 CONFLICT로 전이한다.
     */
    public ThymeleafGenerationStageResult<ThymeleafConversionOperation> apply(
            String operationId, Path projectRoot, LegacyScreenAnalysis currentAnalysis) {
        ThymeleafConversionOperation operation = repository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("THYMELEAF_OPERATION_NOT_FOUND: " + operationId));
        if (operation.status() != ThymeleafConversionOperationStatus.APPROVED) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_NOT_APPROVED: " + operationId + " status=" + operation.status());
        }
        if (!operation.screenId().equals(currentAnalysis.screenId())) {
            throw new IllegalArgumentException(
                    "THYMELEAF_OPERATION_SCREEN_MISMATCH: operation=" + operation.screenId()
                            + ", currentAnalysis=" + currentAnalysis.screenId());
        }

        SourceRevisionRef currentRevision = currentAnalysis.sourceRevision();
        if (hasSourceRevisionConflict(operation.sourceRevision(), currentRevision)) {
            List<GenerationIssue> issues = List.of(fatal("SOURCE_REVISION_CHANGED", STAGE_APPLY,
                    operation.screenId(), "Apply 직전 JSP/Controller/VO가 변경되어 재분석이 필요합니다."));
            ThymeleafConversionOperation conflicted = repository.appendTransition(
                    operationId, ThymeleafConversionOperationStatus.CONFLICT,
                    null, null, currentRevision, issues, operation.artifacts());
            return ThymeleafGenerationStageResult.failure(conflicted.issues());
        }

        LegacySourceInventoryService.WriteResult writeResult;
        try {
            writeResult = inventoryService.writeFileWithBackup(
                    projectRoot, operation.targetRelativePath(), operation.renderedHtml());
        } catch (RuntimeException exception) {
            List<GenerationIssue> issues = List.of(fatal(
                    "APPLY_WRITE_FAILED", STAGE_APPLY, operation.screenId(), exception.getMessage()));
            repository.appendTransition(operationId, ThymeleafConversionOperationStatus.FAILED,
                    null, null, null, issues, operation.artifacts());
            return ThymeleafGenerationStageResult.failure(issues);
        }

        List<ArtifactRef> artifacts = buildApplyArtifacts(operationId, operation, writeResult);
        ThymeleafConversionOperation applied = repository.transitionToApplied(operationId, true, artifacts);

        String appliedContent = readWrittenFile(writeResult.writtenPath());
        ThymeleafRenderValidator.RenderReport postApplyReport = validateHtmlContent(appliedContent);
        if (!postApplyReport.passed()) {
            List<GenerationIssue> issues = List.of(fatal("POST_APPLY_VALIDATION_FAILED", STAGE_APPLY,
                    operation.screenId(), String.join("; ", postApplyReport.failures())));
            repository.appendTransition(operationId, ThymeleafConversionOperationStatus.FAILED,
                    null, null, null, issues, artifacts);
            return ThymeleafGenerationStageResult.failure(issues);
        }

        ThymeleafConversionOperation validated = repository.transitionToValidated(operationId, true, List.of());
        return ThymeleafGenerationStageResult.success(validated, List.of());
    }

    private boolean hasSourceRevisionConflict(SourceRevisionRef captured, SourceRevisionRef current) {
        return captured != null && current != null
                && !captured.revisionToken().equals(current.revisionToken());
    }

    private List<ArtifactRef> buildApplyArtifacts(
            String operationId, ThymeleafConversionOperation operation,
            LegacySourceInventoryService.WriteResult writeResult) {
        List<ArtifactRef> artifacts = new ArrayList<>();
        artifacts.add(new ArtifactRef(
                operationId + "-applied", "THYMELEAF_APPLIED_FILE", writeResult.writtenPath().toString(),
                operationHashFactory.sha256Hex(operation.renderedHtml().getBytes(StandardCharsets.UTF_8)),
                Instant.now()));
        if (writeResult.backupPath() != null) {
            String backupContent = readWrittenFile(writeResult.backupPath());
            artifacts.add(new ArtifactRef(
                    operationId + "-backup", "THYMELEAF_APPLY_BACKUP", writeResult.backupPath().toString(),
                    operationHashFactory.sha256Hex(backupContent.getBytes(StandardCharsets.UTF_8)), Instant.now()));
        }
        return artifacts;
    }

    private ThymeleafRenderValidator.RenderReport validateHtmlContent(String html) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("thymeleaf-preview-validate-");
            Files.writeString(tempDir.resolve("preview.html"), html, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        try {
            return renderValidator.validateDirectory(tempDir.toString());
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private void deleteQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 임시 검증 디렉터리 정리 실패는 기능적으로 무해하다.
                        }
                    });
        } catch (IOException ignored) {
            // 임시 검증 디렉터리 정리 실패는 기능적으로 무해하다.
        }
    }

    private String readWrittenFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private GenerationIssue fatal(String code, String stage, String sourceLocation, String message) {
        return issueFactory.issue(code, GenerationIssue.Severity.FATAL, stage, sourceLocation, message, null);
    }
}
