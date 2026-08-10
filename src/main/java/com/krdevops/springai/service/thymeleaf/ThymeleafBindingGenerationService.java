package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacySourceManifest;
import com.krdevops.springai.model.thymeleaf.RegenerationDiffResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import com.krdevops.springai.service.ScreenSpecificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WP6 생성 엔트리포인트. 안전하게 읽은 JSP·Controller·VO 증거를 Binding Contract와 Thymeleaf
 * HTML로 조립한 뒤, 성공한 결과만 기존 {@link ThymeleafProjectWorkflowService#preview}에 넘긴다.
 * 승인·Apply·rollback 상태기계는 새로 만들지 않고 기존 Project Workflow를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class ThymeleafBindingGenerationService {

    private static final String CONTRACT_STAGE = "BINDING_CONTRACT";
    private static final String COMPOSE_STAGE = "BINDING_COMPOSE";
    private static final String PREVIEW_STAGE = "PREVIEW_READY";
    private static final String REGENERATION_DIFF_STAGE = "REGENERATION_DIFF";

    private final LegacySourceInventoryService inventoryService;
    private final JspSourceReader jspSourceReader;
    private final ControllerSourceReader controllerSourceReader;
    private final VoSourceReader voSourceReader;
    private final BindingContractAssembler contractAssembler;
    private final BindingComposer bindingComposer;
    private final ScreenSpecificationService screenSpecificationService;
    private final ThymeleafProjectWorkflowService workflowService;
    private final RegenerationDiffService regenerationDiffService;

    public BindingPreviewResult preview(ThymeleafBindingPreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 필수입니다.");
        }
        Path projectRoot = Path.of(request.projectRootPath());
        SourceReadBudget budget = SourceReadBudget.defaultBudget();
        List<LegacySourceInventoryService.ReadSourceFile> legacySources = new ArrayList<>();

        LegacySourceInventoryService.ReadSourceFile jsp = read(
                projectRoot, request.jspRelativePath(), budget, legacySources);
        LegacySourceInventoryService.ReadSourceFile controller = read(
                projectRoot, request.controllerRelativePath(), budget, legacySources);
        LegacySourceInventoryService.ReadSourceFile vo = read(
                projectRoot, request.voRelativePath(), budget, legacySources);
        LegacySourceInventoryService.ReadSourceFile voSuperclass = readOptional(
                projectRoot, request.voSuperclassRelativePath(), budget, legacySources);
        LegacySourceInventoryService.ReadSourceFile secondaryVo = readOptional(
                projectRoot, request.secondaryVoRelativePath(), budget, legacySources);

        VoEvidence primaryVoEvidence = voSourceReader.read(
                vo.relativePath(), vo.content(), voSuperclass == null ? null : voSuperclass.content());
        VoEvidence secondaryVoEvidence = secondaryVo == null ? null
                : voSourceReader.read(secondaryVo.relativePath(), secondaryVo.content());
        LegacySourceManifest sourceManifest = inventoryService.sourceManifest(legacySources);
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                request.screenId(), sourceManifest.fingerprint(), Instant.now());
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                request.screenId(), request.screenRole(),
                jspSourceReader.read(jsp.relativePath(), jsp.content()),
                controllerSourceReader.read(controller.relativePath(), controller.content()),
                primaryVoEvidence, sourceRevision, List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> contractResult =
                contractAssembler.assemble(analysis, secondaryVoEvidence);
        if (!contractResult.successful()) {
            return BindingPreviewResult.blocked(CONTRACT_STAGE, request.outputRelativePath(),
                    null, contractResult.issues());
        }

        ThymeleafBindingContract candidate = contractResult.value();
        ScreenSpecification screenSpecification = approvedSpecification(request.screenSpecificationId());
        ThymeleafGenerationStageResult<String> composeResult = bindingComposer.compose(
                candidate, request.pageTitle(), request.layoutView(),
                screenSpecification, request.registRoute());
        if (!composeResult.successful()) {
            return BindingPreviewResult.blocked(COMPOSE_STAGE, request.outputRelativePath(),
                    candidate, composeResult.issues());
        }

        // 정적 계약/composе 검증을 통과한 뒤에만 이전 Operation을 조회한다 — 그 전에 조회하면
        // 어차피 다른 이유로 차단될 요청 때문에 store를 불필요하게 건드리고, "차단 시 workflow와
        // 상호작용 없음"을 가정하는 기존 테스트(예: REVIEW_REQUIRED가 compose 단계에서 막히는
        // 케이스)가 깨진다.
        Optional<ThymeleafOperationSnapshot> previousOperation =
                workflowService.findLatestByScreen(projectRoot, request.screenId());
        RegenerationDiffResult regenerationDiff = regenerationDiffService.diff(
                previousOperation.map(ThymeleafOperationSnapshot::bindingContract).orElse(null), candidate);
        if (regenerationDiff.requiresReview()) {
            List<GenerationIssue> issues = new ArrayList<>(composeResult.issues());
            issues.add(new GenerationIssue(
                    "REGENERATION_PERMISSION_OR_CSRF_CHANGED", GenerationIssue.Severity.ERROR, CONTRACT_STAGE,
                    null, regenerationDiffMessage(regenerationDiff),
                    "재생성된 화면이 이전과 동일한 권한/CSRF 보호를 적용하는지 확인한 뒤 다시 승인하세요."));
            return BindingPreviewResult.blocked(REGENERATION_DIFF_STAGE, request.outputRelativePath(),
                    candidate, issues);
        }

        ThymeleafProjectWorkflowService.WorkflowResult workflow = workflowService.preview(
                projectRoot, Map.of(request.outputRelativePath(), composeResult.value()),
                candidate, sourceManifest);
        return new BindingPreviewResult(true, PREVIEW_STAGE, request.outputRelativePath(),
                candidate, composeResult.issues(), workflow);
    }

    private String regenerationDiffMessage(RegenerationDiffResult diff) {
        StringBuilder message = new StringBuilder("이전 적용 계약 대비 재생성 결과가 달라졌습니다.");
        if (diff.permissionChanged()) {
            message.append(" 권한 evidence 추가: ").append(diff.addedSecurityEvidence())
                    .append(", 제거: ").append(diff.removedSecurityEvidence()).append('.');
        }
        if (diff.httpMethodChanged()) {
            message.append(" HTTP method 변경(CSRF 보호 상태 변경 가능): ")
                    .append(diff.previousHttpMethod()).append(" -> ").append(diff.currentHttpMethod()).append('.');
        }
        return message.toString();
    }

    private LegacySourceInventoryService.ReadSourceFile read(
            Path root, String relativePath, SourceReadBudget budget,
            List<LegacySourceInventoryService.ReadSourceFile> legacySources) {
        LegacySourceInventoryService.ReadSourceFile source =
                inventoryService.readSourceFile(root, relativePath, budget);
        legacySources.add(source);
        return source;
    }

    private LegacySourceInventoryService.ReadSourceFile readOptional(
            Path root, String relativePath, SourceReadBudget budget,
            List<LegacySourceInventoryService.ReadSourceFile> legacySources) {
        return relativePath == null ? null : read(root, relativePath, budget, legacySources);
    }

    private ScreenSpecification approvedSpecification(String specificationId) {
        if (specificationId == null) {
            return null;
        }
        ScreenSpecification specification = screenSpecificationService.get(specificationId);
        if (specification.status() != ScreenSpecStatus.APPROVED) {
            throw new IllegalStateException("APPROVED 화면명세만 Binding 생성에 사용할 수 있습니다: "
                    + specification.id() + " (" + specification.status() + ")");
        }
        return specification;
    }

    public record BindingPreviewResult(
            boolean successful,
            String completedStage,
            String outputRelativePath,
            ThymeleafBindingContract bindingContract,
            List<GenerationIssue> issues,
            ThymeleafProjectWorkflowService.WorkflowResult workflow
    ) {
        public BindingPreviewResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (successful && workflow == null) {
                throw new IllegalArgumentException("successful=true면 workflow는 필수입니다.");
            }
            if (!successful && workflow != null) {
                throw new IllegalArgumentException("successful=false면 workflow는 null이어야 합니다.");
            }
        }

        static BindingPreviewResult blocked(
                String stage, String outputRelativePath, ThymeleafBindingContract contract,
                List<GenerationIssue> issues) {
            return new BindingPreviewResult(false, stage, outputRelativePath, contract, issues, null);
        }
    }
}
