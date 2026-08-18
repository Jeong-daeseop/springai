package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacySourceManifest;
import com.krdevops.springai.model.thymeleaf.RegenerationDiffResult;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.VoEvidence;
import com.krdevops.springai.service.ScreenSpecificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final LegacyScreenRoleResolver screenRoleResolver;
    private final DesignMdRuleLoader designRuleLoader;
    private final BindingContractAssembler contractAssembler;
    private final BindingComposer bindingComposer;
    private final CompanyDesignTokenResolver designTokenResolver;
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
        List<GenerationIssue> screenRoleAdvisories = screenRoleMismatchAdvisory(request, analysis);

        ThymeleafGenerationStageResult<ThymeleafBindingContract> contractResult =
                contractAssembler.assemble(analysis, secondaryVoEvidence);
        if (!contractResult.successful()) {
            return BindingPreviewResult.blocked(CONTRACT_STAGE, request.outputRelativePath(),
                    null, contractResult.issues());
        }

        ThymeleafBindingContract candidate = contractResult.value();
        ScreenSpecification screenSpecification = approvedSpecification(request.screenSpecificationId());
        ResolvedDesignTokens resolvedDesignTokens = resolveDesignTokens(request);
        ThymeleafGenerationStageResult<String> composeResult = bindingComposer.compose(
                candidate, request.pageTitle(), request.layoutView(),
                screenSpecification, request.registRoute(), resolvedDesignTokens);
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
        List<GenerationIssue> finalIssues = new ArrayList<>(composeResult.issues());
        finalIssues.addAll(screenRoleAdvisories);
        return new BindingPreviewResult(true, PREVIEW_STAGE, request.outputRelativePath(),
                candidate, finalIssues, workflow);
    }

    /**
     * R6-053: JSP·Controller 소스 증거로 추정한 화면 유형이 호출자가 명시한
     * {@code screenRole}과 어긋나면 WARNING으로 알린다. 자동 판정이 필수 입력을 대체하지
     * 않으므로 전체 Preview를 막지 않는다 — confidence가 낮은(근거 부족) 제안은 알리지 않는다.
     */
    private List<GenerationIssue> screenRoleMismatchAdvisory(
            ThymeleafBindingPreviewRequest request, LegacyScreenAnalysis analysis) {
        var suggestion = screenRoleResolver.suggest(analysis.jsp(), analysis.controller());
        if (!suggestion.resolved() || suggestion.confidence() < 0.7
                || suggestion.suggestedRole() == request.screenRole()) {
            return List.of();
        }
        return List.of(new GenerationIssue(
                "SCREEN_ROLE_MISMATCH_WITH_SOURCE_EVIDENCE", GenerationIssue.Severity.WARNING, CONTRACT_STAGE,
                null,
                "요청한 screenRole(" + request.screenRole() + ")이 소스 증거로 추정한 화면 유형("
                        + suggestion.suggestedRole() + ", confidence=" + suggestion.confidence()
                        + ")과 다릅니다: " + suggestion.reasoning(),
                "screenRole 지정이 맞는지 다시 확인하세요."));
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

    /**
     * R6-057: {@code designSystemProfileId}가 주어진 경우에만 회사 Design Token을 해석해
     * provenance 주석 재료로 쓴다. 이 화면이 생성되는지 여부는 업무 Binding Contract만으로
     * 결정되므로(Design Token은 부가 정보) 해석 실패는 FATAL로 전체 Preview를 막지 않고
     * 경고만 남긴 뒤 토큰 없이 계속 진행한다.
     */
    private ResolvedDesignTokens resolveDesignTokens(ThymeleafBindingPreviewRequest request) {
        if (request.designSystemProfileId() == null) {
            return null;
        }
        AppliedDesignRules appliedDesignRules = loadAppliedDesignRules(request.projectRootPath());
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result =
                designTokenResolver.resolve(request.designSystemProfileId(), appliedDesignRules);
        if (!result.successful()) {
            log.warn("Design Token 해석 실패, provenance 없이 진행합니다: profileId={}, issues={}",
                    request.designSystemProfileId(), result.issues());
            return null;
        }
        return result.value();
    }

    /**
     * R6-062: Profile/Registry 기본 Token과 DESIGN.md의 화면 Override를 실제로 병합하기 위해
     * 로드한다. 이전에는 이 자리에 항상 {@code null}이 하드코딩돼 있어 DESIGN.md가 있어도
     * {@link CompanyDesignTokenResolver}의 override 병합 로직(구현·테스트는 있었음)이 생성
     * 파이프라인에서 한 번도 실행되지 않았다 — Registry의 신규 소비자가 없어 죽은 코드였던
     * R6-057과 같은 종류의 배선 누락. 업무 계약 침범(FATAL) 차단은 여기서 다시 하지 않는다 —
     * 그 차단은 {@code ThymeleafProjectWorkflowService.preview()}가 같은 DESIGN.md를 별도로
     * 다시 읽어 이미 강제하므로, 여기서는 Token 병합에만 쓰고 실패해도 병합 없이 계속 진행한다.
     * DESIGN.md가 없거나 병합할 규칙이 비어 있으면 {@code null}을 반환해 기존 동작(Registry
     * 기본값만 사용)과 동일하게 유지한다.
     */
    private AppliedDesignRules loadAppliedDesignRules(String projectRootPath) {
        ThymeleafGenerationStageResult<AppliedDesignRules> result = designRuleLoader.load(projectRootPath);
        if (!result.successful()) {
            log.warn("DESIGN.md 규칙 로드 실패, Design Token 병합 없이 진행합니다: issues={}", result.issues());
            return null;
        }
        AppliedDesignRules rules = result.value();
        return rules != null && !rules.appliedRules().isEmpty() ? rules : null;
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
