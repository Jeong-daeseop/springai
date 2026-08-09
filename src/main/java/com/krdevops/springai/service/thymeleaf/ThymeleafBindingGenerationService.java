package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import com.krdevops.springai.service.ScreenSpecificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final LegacySourceInventoryService inventoryService;
    private final JspSourceReader jspSourceReader;
    private final ControllerSourceReader controllerSourceReader;
    private final VoSourceReader voSourceReader;
    private final BindingContractAssembler contractAssembler;
    private final BindingComposer bindingComposer;
    private final ScreenSpecificationService screenSpecificationService;
    private final ThymeleafProjectWorkflowService workflowService;

    public BindingPreviewResult preview(ThymeleafBindingPreviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 필수입니다.");
        }
        Path projectRoot = Path.of(request.projectRootPath());
        SourceReadBudget budget = SourceReadBudget.defaultBudget();
        List<String> sourceHashes = new ArrayList<>();

        LegacySourceInventoryService.ReadSourceFile jsp = read(
                projectRoot, request.jspRelativePath(), budget, sourceHashes);
        LegacySourceInventoryService.ReadSourceFile controller = read(
                projectRoot, request.controllerRelativePath(), budget, sourceHashes);
        LegacySourceInventoryService.ReadSourceFile vo = read(
                projectRoot, request.voRelativePath(), budget, sourceHashes);
        LegacySourceInventoryService.ReadSourceFile voSuperclass = readOptional(
                projectRoot, request.voSuperclassRelativePath(), budget, sourceHashes);
        LegacySourceInventoryService.ReadSourceFile secondaryVo = readOptional(
                projectRoot, request.secondaryVoRelativePath(), budget, sourceHashes);

        VoEvidence primaryVoEvidence = voSourceReader.read(
                vo.relativePath(), vo.content(), voSuperclass == null ? null : voSuperclass.content());
        VoEvidence secondaryVoEvidence = secondaryVo == null ? null
                : voSourceReader.read(secondaryVo.relativePath(), secondaryVo.content());
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                request.screenId(), inventoryService.projectFingerprint(sourceHashes), Instant.now());
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

        ScreenSpecification screenSpecification = approvedSpecification(request.screenSpecificationId());
        ThymeleafGenerationStageResult<String> composeResult = bindingComposer.compose(
                contractResult.value(), request.pageTitle(), request.layoutView(),
                screenSpecification, request.registRoute());
        if (!composeResult.successful()) {
            return BindingPreviewResult.blocked(COMPOSE_STAGE, request.outputRelativePath(),
                    contractResult.value(), composeResult.issues());
        }

        ThymeleafProjectWorkflowService.WorkflowResult workflow = workflowService.preview(
                projectRoot, Map.of(request.outputRelativePath(), composeResult.value()));
        return new BindingPreviewResult(true, PREVIEW_STAGE, request.outputRelativePath(),
                contractResult.value(), composeResult.issues(), workflow);
    }

    private LegacySourceInventoryService.ReadSourceFile read(
            Path root, String relativePath, SourceReadBudget budget, List<String> sourceHashes) {
        LegacySourceInventoryService.ReadSourceFile source =
                inventoryService.readSourceFile(root, relativePath, budget);
        sourceHashes.add(source.sha256Hex());
        return source;
    }

    private LegacySourceInventoryService.ReadSourceFile readOptional(
            Path root, String relativePath, SourceReadBudget budget, List<String> sourceHashes) {
        return relativePath == null ? null : read(root, relativePath, budget, sourceHashes);
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
