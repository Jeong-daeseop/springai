package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.GenerationWarning;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import com.krdevops.springai.service.generation.pipeline.processor.SharedProcessorIds;
import com.krdevops.springai.service.controlplane.CrudGenerationAuditPort;
import com.krdevops.springai.service.designsystem.RequiredComponentMappingApplyGate;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.migration.LegacyCompatibilityService;
import com.krdevops.springai.service.migration.PipelineMigrationGuard;
import com.krdevops.springai.model.renderer.RendererCapabilityRequirement;
import com.krdevops.springai.model.renderer.RendererFeature;
import com.krdevops.springai.model.renderer.RendererFallback;
import com.krdevops.springai.model.renderer.RendererProfile;
import com.krdevops.springai.service.renderer.RendererCapabilityMatrixService;
import com.krdevops.springai.service.renderer.RendererProfileLoader;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * CRUD 생성 Blueprint를 조립한다. 명세서 §12.1.
 *
 * <p>파일 저장·이력 저장·MCP 문자열 생성은 하지 않는다. 생성이 중단되어야 하는 4가지 사유
 * (테이블 없음 / 메타데이터 모호 / alias 충돌 / layout 없음)에는 Blueprint를 만들지 않고
 * {@link CrudPlanFailure}를 돌려주며, 이 경우 Renderer/Executor/Processor/Verifier는 전혀
 * 실행되지 않는다.
 */
@Slf4j
@Component
public class CrudGenerationPlanner {

    private static final String PACKAGE_PREFIX = "egovframework.let.";

    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudProgramMetadataService crudProgramMetadataService;
    private final GenerationDesignContextService generationDesignContextService;
    private final CrudModelFactory crudModelFactory;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final BoardRouteCollisionDetector routeCollisionDetector;
    private final RequiredComponentMappingApplyGate componentMappingApplyGate;
    private final RendererProfileLoader rendererProfileLoader;
    private final RendererCapabilityMatrixService rendererCapabilityMatrixService;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final PipelineMigrationGuard pipelineMigrationGuard;
    private final LegacyCompatibilityService legacyCompatibilityService;
    private final CrudGenerationApprovalPolicy approvalPolicy;
    private final CrudGenerationAuditPort auditPort;

    @Autowired
    public CrudGenerationPlanner(
            CrudSchemaQueryService crudSchemaQueryService,
            CrudProgramMetadataService crudProgramMetadataService,
            GenerationDesignContextService generationDesignContextService,
            CrudModelFactory crudModelFactory,
            ThymeleafLayoutValidator thymeleafLayoutValidator,
            BoardRouteCollisionDetector routeCollisionDetector,
            RequiredComponentMappingApplyGate componentMappingApplyGate,
            RendererProfileLoader rendererProfileLoader,
            RendererCapabilityMatrixService rendererCapabilityMatrixService,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            PipelineMigrationGuard pipelineMigrationGuard,
            LegacyCompatibilityService legacyCompatibilityService,
            CrudGenerationApprovalPolicy approvalPolicy,
            CrudGenerationAuditPort auditPort) {
        this.crudSchemaQueryService = crudSchemaQueryService;
        this.crudProgramMetadataService = crudProgramMetadataService;
        this.generationDesignContextService = generationDesignContextService;
        this.crudModelFactory = crudModelFactory;
        this.thymeleafLayoutValidator = thymeleafLayoutValidator;
        this.routeCollisionDetector = routeCollisionDetector;
        this.componentMappingApplyGate = componentMappingApplyGate;
        this.rendererProfileLoader = rendererProfileLoader;
        this.rendererCapabilityMatrixService = rendererCapabilityMatrixService;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.pipelineMigrationGuard = pipelineMigrationGuard;
        this.legacyCompatibilityService = legacyCompatibilityService;
        this.approvalPolicy = approvalPolicy;
        this.auditPort = auditPort == null ? CrudGenerationAuditPort.none() : auditPort;
    }

    /** APR-B03 도입 전 12-arg 호출자 호환. */
    public CrudGenerationPlanner(
            CrudSchemaQueryService crudSchemaQueryService,
            CrudProgramMetadataService crudProgramMetadataService,
            GenerationDesignContextService generationDesignContextService,
            CrudModelFactory crudModelFactory,
            ThymeleafLayoutValidator thymeleafLayoutValidator,
            BoardRouteCollisionDetector routeCollisionDetector,
            RequiredComponentMappingApplyGate componentMappingApplyGate,
            RendererProfileLoader rendererProfileLoader,
            RendererCapabilityMatrixService rendererCapabilityMatrixService,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            PipelineMigrationGuard pipelineMigrationGuard,
            LegacyCompatibilityService legacyCompatibilityService) {
        this(crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                componentMappingApplyGate, rendererProfileLoader, rendererCapabilityMatrixService,
                pipelineEvolutionProperties, pipelineMigrationGuard, legacyCompatibilityService,
                null, CrudGenerationAuditPort.none());
    }

    /** MAP-012/R2-007 도입 전 7-arg 호출자 호환. */
    public CrudGenerationPlanner(
            CrudSchemaQueryService crudSchemaQueryService,
            CrudProgramMetadataService crudProgramMetadataService,
            GenerationDesignContextService generationDesignContextService,
            CrudModelFactory crudModelFactory,
            ThymeleafLayoutValidator thymeleafLayoutValidator,
            BoardRouteCollisionDetector routeCollisionDetector,
            RequiredComponentMappingApplyGate componentMappingApplyGate) {
        this(crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                componentMappingApplyGate, null, null,
                new PipelineEvolutionProperties(), new PipelineMigrationGuard(),
                new LegacyCompatibilityService());
    }

    /** MAP-012 도입 전 단위 테스트·Java 호출자 호환. */
    public CrudGenerationPlanner(
            CrudSchemaQueryService crudSchemaQueryService,
            CrudProgramMetadataService crudProgramMetadataService,
            GenerationDesignContextService generationDesignContextService,
            CrudModelFactory crudModelFactory,
            ThymeleafLayoutValidator thymeleafLayoutValidator,
            BoardRouteCollisionDetector routeCollisionDetector) {
        this(crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector, null);
    }

    public CrudGenerationPlan plan(CrudGenerationCommand command) {
        String database = command.database();
        String tableName = command.tableName();
        String domain = command.domain();
        String packageName = command.packageName();
        String outputPath = command.outputPath().toString();
        String egovVersion = command.egovVersion();
        CrudGenerationOptions options = command.toGenerationOptions();

        CrudViewType viewType = CrudViewType.from(command.viewType());
        CrudLayoutMode layoutMode = viewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(command.layout().layoutMode())
                : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference layoutReference = viewType == CrudViewType.THYMELEAF
                ? thymeleafLayoutValidator.resolve(
                        command.layout().layoutView(), command.layout().breadcrumbView())
                : thymeleafLayoutValidator.resolve(null, null);
        log.info("[plan] 시작: table={}, domain={}, outputPath={}, egovVersion={}, viewType={}",
                 tableName, domain, outputPath, egovVersion, viewType.value());

        // V2_APPLY 필수화: Thymeleaf 생성은 승인된 화면명세(Figma 디자인 참조) 없이는 더 이상
        // 스키마만으로 진행할 수 없다 — RequiredComponentMappingApplyGate가 결국 막을 것을
        // DB 조회도 하기 전에 조기에, 다음 행동을 알려주는 메시지와 함께 막는다.
        if (viewType == CrudViewType.THYMELEAF && pipelineEvolutionProperties.usesV2Apply()
                && isBlank(options.designReferenceId()) && isBlank(options.screenSpecificationId())) {
            log.warn("[plan] V2_APPLY: 승인된 화면명세 없이 Thymeleaf 생성 시도 차단: table={}", tableName);
            return CrudGenerationPlan.rejected(new CrudPlanFailure(
                    CrudPlanFailure.Kind.MAPPING_BLOCKED,
                    "V2_APPLY 모드에서는 Thymeleaf 생성 전 Figma 디자인 참조로 승인된 화면명세가 필요합니다",
                    List.of(
                            "1. analyzeFigmaReference(figmaUrl, nodeId, featureType=\"crud\") 호출 → 분석 ID 획득",
                            "2. createScreenSpecification(database, tableName, screenName, featureType=\"crud\", "
                                    + "designAnalysisId) 호출 — APPROVED면 바로 사용, REVIEW_REQUIRED면 "
                                    + "reviseScreenSpecification() 후 approveScreenSpecification() 호출",
                            "3. buildFullCrudPrompt(..., screenSpecificationId=승인된 화면명세 ID)로 다시 호출"),
                    null, null, null, null, List.of()));
        }

        // CRUD_명시적_승인_단계_구현_명세서.md §4 옵션 B: 고위험으로 지정된 테이블(또는 전체)은
        // viewType과 무관하게 승인된 화면명세 없이는 auto 생성을 차단한다. 위 V2_APPLY 가드와
        // 달리 이 정책은 Thymeleaf에 한정되지 않는다 — approvalRequiredForAll이면 JSP도 막힌다.
        if (approvalPolicy != null && approvalPolicy.requiresApproval(tableName)
                && isBlank(options.designReferenceId()) && isBlank(options.screenSpecificationId())) {
            log.warn("[plan] 고위험 테이블 승인 정책: 승인된 화면명세 없이 생성 시도 차단: table={}", tableName);
            recordApprovalPolicyBlocked(outputPath, tableName, viewType.value());
            return CrudGenerationPlan.rejected(new CrudPlanFailure(
                    CrudPlanFailure.Kind.MAPPING_BLOCKED,
                    "이 테이블은 승인된 화면명세 없이 생성이 차단됩니다(고위험 테이블 정책)",
                    List.of(
                            "1. analyzeFigmaReference(figmaUrl, nodeId, featureType=\"crud\") 호출 → 분석 ID 획득",
                            "2. createScreenSpecification(database, tableName, screenName, featureType=\"crud\", "
                                    + "designAnalysisId) 호출 — APPROVED면 바로 사용, REVIEW_REQUIRED면 "
                                    + "reviseScreenSpecification() 후 approveScreenSpecification() 호출",
                            "3. buildFullCrudPrompt(..., screenSpecificationId=승인된 화면명세 ID)로 다시 호출"),
                    null, null, null, null, List.of()));
        }

        List<Map<String, Object>> rawColumns = crudSchemaQueryService.fetchColumns(database, tableName);
        if (rawColumns.isEmpty()) {
            log.warn("[plan] 테이블 없음: {}.{}", database, tableName);
            return CrudGenerationPlan.rejected(CrudPlanFailure.tableNotFound());
        }

        // CrudLayerDefinition 템플릿 경로가 egovframework/let/{PKG}/... 고정이므로
        // packageName이 이 형식이 아니면 경로가 오계산된다 — 조기 실패 처리.
        if (packageName == null || !packageName.startsWith(PACKAGE_PREFIX)) {
            throw new IllegalArgumentException(
                "packageName은 egovframework.let.* 형식이어야 합니다: " + packageName);
        }
        String pkgSub = packageName.replace(PACKAGE_PREFIX, "").replace(".", "/");

        CrudProgramMetadata metadata =
                crudProgramMetadataService.resolve(database, domain, tableName, options);
        List<String> warnings = new ArrayList<>();
        if (metadata.message() != null) warnings.add(metadata.message());
        if (metadata.blocksGeneration()) {
            return CrudGenerationPlan.rejected(new CrudPlanFailure(
                    CrudPlanFailure.Kind.METADATA_BLOCKED, "메타데이터 검증 실패",
                    List.of(metadata.message()), metadata.menuIntegrationStatus(),
                    metadata.programKoreanName(), null, null, warnings));
        }

        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                database, tableName, metadata.programKoreanName(), "crud",
                options.designReferenceId(), options.screenSpecificationId());

        // R0-DUAL: v2 Design IR 참조가 붙은 명세로 실제 Apply를 시도하는지 여부.
        // OBSERVE/DUAL_READ 단계는 v2를 관찰·비교만 해야 하므로, 이 상태에서 v2 Apply가
        // 시도되면 조용히 legacy로 넘기지 않고 즉시 fail-closed 한다.
        boolean v2ApplyRequested = screenSpecification != null
                && screenSpecification.uiDesignSpecReference() != null;
        PipelineEvolutionProperties.Mode mode = pipelineEvolutionProperties.getMode();
        pipelineMigrationGuard.requireLegacyApply(
                mode == PipelineEvolutionProperties.Mode.OBSERVE, v2ApplyRequested);
        legacyCompatibilityService.requireLegacyApplyDuringDualRead(
                mode == PipelineEvolutionProperties.Mode.DUAL_READ, v2ApplyRequested);

        ScreenSubsetMode subsetMode = viewType == CrudViewType.THYMELEAF
                ? ScreenSubsetMode.LIST_AND_DETAIL : ScreenSubsetMode.LIST_ONLY;
        CrudTemplateModel model = crudModelFactory.fromSchema(
                tableName, domain, packageName, egovVersion, rawColumns, metadata,
                viewType, subsetMode, screenSpecification);
        if (viewType == CrudViewType.THYMELEAF && componentMappingApplyGate != null) {
            try {
                model = crudModelFactory.withDesignComponents(model,
                        componentMappingApplyGate.requireForApply(screenSpecification,
                                RequiredComponentMappingApplyGate.THYMELEAF_KRDS_PROFILE));
            } catch (RequiredComponentMappingApplyGate.RequiredComponentMappingException exception) {
                return CrudGenerationPlan.rejected(new CrudPlanFailure(
                        CrudPlanFailure.Kind.MAPPING_BLOCKED, "Component Mapping Apply Gate 실패",
                        exception.issues(), metadata.menuIntegrationStatus(),
                        metadata.programKoreanName(), null, model.route().canonicalListPath(), warnings));
            }
        }

        // R2-007: Thymeleaf 생성은 Command가 고정한 승인 RendererProfile과
        // 화면/스키마 요구 Capability를 생성 전(preflight)에 반드시 통과해야 한다.
        // 레거시 6/7-arg fixture는 이 협력자를 주입하지 않으므로 기존 JSP 테스트 경로를 보존한다.
        if (viewType == CrudViewType.THYMELEAF
                && rendererProfileLoader != null && rendererCapabilityMatrixService != null) {
            try {
                RendererProfile profile = rendererProfileLoader.loadApproved(
                        command.rendererProfileReference().profileId(),
                        command.rendererProfileReference().version());
                if (!command.rendererProfileReference().identifies(profile)) {
                    return rendererCapabilityFailure(metadata, model,
                            List.of("RENDERER_PROFILE_REFERENCE_MISMATCH: Command의 RendererProfile ID·Version·Hash가 승인 Profile과 일치하지 않습니다."),
                            warnings);
                }
                rendererCapabilityMatrixService.requireSupported(profile,
                        rendererCapabilityRequirement(model, screenSpecification, layoutMode));
            } catch (RendererCapabilityMatrixService.RendererCapabilityException exception) {
                List<String> issues = exception.assessment().issues().stream()
                        .map(issue -> issue.code() + "[" + issue.target() + "]: " + issue.message())
                        .toList();
                return rendererCapabilityFailure(metadata, model, issues, warnings);
            } catch (RuntimeException exception) {
                return rendererCapabilityFailure(metadata, model,
                        List.of("RENDERER_PROFILE_LOAD_OR_VALIDATE_FAILED: " + exception.getMessage()), warnings);
            }
        }

        if (viewType == CrudViewType.JSP && detailSubsetRequested(screenSpecification)) {
            warnings.add("JSP 생성에서는 detail 화면 필드 subset을 지원하지 않아 표준 상세 필드를 사용합니다.");
        }

        Map<String, String> aliasConflicts =
                checkAliasConflicts(model, domain, outputPath, pkgSub, viewType);
        if (!aliasConflicts.isEmpty()) {
            String message = "Controller URL alias 충돌(ambiguous mapping 위험): " + aliasConflicts;
            return CrudGenerationPlan.rejected(new CrudPlanFailure(
                    CrudPlanFailure.Kind.ALIAS_CONFLICT, "URL 검증 실패", List.of(message),
                    metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                    model.route().registeredListPath(), model.route().canonicalListPath(), warnings));
        }

        if (viewType == CrudViewType.THYMELEAF && layoutMode == CrudLayoutMode.REUSE) {
            ThymeleafLayoutValidator.LayoutValidationResult validation =
                    thymeleafLayoutValidator.validateExisting(
                            outputPath, layoutReference.layoutView(), layoutReference.breadcrumbView());
            if (!validation.valid()) {
                return CrudGenerationPlan.rejected(CrudPlanFailure.layoutMissing(
                        thymeleafLayoutValidator.missingLayoutMessage(outputPath, validation)));
            }
        }

        GenerationContext context = new GenerationContext(
                "crud", database, tableName, domain, packageName, outputPath, egovVersion,
                viewType.value(),
                attributes(model, viewType, layoutMode, layoutReference, command.designSystemProfileId()));

        GenerationBlueprint blueprint = new GenerationBlueprint(
                context,
                fileBlueprints(model, domain, outputPath, pkgSub, viewType, layoutMode, layoutReference),
                processorSteps(),
                warnings.stream().map(GenerationWarning::new).toList());
        return new CrudGenerationPlan(blueprint, null, metadata, model, warnings);
    }

    private static Map<String, Object> attributes(
            CrudTemplateModel model, CrudViewType viewType, CrudLayoutMode layoutMode,
            ThymeleafLayoutValidator.LayoutReference layoutReference, String designSystemProfileId) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(CrudGenerationAttributes.MODEL, model);
        attributes.put(CrudGenerationAttributes.VIEW_TYPE, viewType);
        attributes.put(CrudGenerationAttributes.LAYOUT_MODE, layoutMode);
        attributes.put(CrudGenerationAttributes.LAYOUT_REFERENCE, layoutReference);
        if (designSystemProfileId != null) {
            attributes.put(CrudGenerationAttributes.DESIGN_SYSTEM_PROFILE_ID, designSystemProfileId);
        }
        return attributes;
    }

    /**
     * WP-0 {@code CrudOrchestrationProcessorOrderTest}가 실측한 실제 실행 순서를 선언한다.
     * Directory 검증/Contract 감사는 {@code GenerationVerifier}로 분리되어 있어 여기 없다.
     */
    private static List<ProcessorStep> processorSteps() {
        return List.of(
                new ProcessorStep(KrdsAssetVerificationProcessor.ID,
                        GenerationStage.PRE_WRITE, 90, FailurePolicy.STOP),
                new ProcessorStep(CrudDesignMdCssProcessor.ID,
                        GenerationStage.PRE_WRITE, 95, FailurePolicy.CONTINUE),
                new ProcessorStep(CrudTableDensityCssProcessor.ID,
                        GenerationStage.PRE_WRITE, 100, FailurePolicy.STOP),
                new ProcessorStep(CrudFormColumnCssProcessor.ID,
                        GenerationStage.PRE_WRITE, 110, FailurePolicy.STOP),
                new ProcessorStep(CrudComponentFragmentProcessor.ID,
                        GenerationStage.PRE_WRITE, 115, FailurePolicy.STOP),
                new ProcessorStep(CrudEntryPointProcessor.ID,
                        GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.THYMELEAF_RUNTIME,
                        GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.CONTROLLER_SCAN,
                        GenerationStage.POST_WRITE, 210, FailurePolicy.CONTINUE),
                new ProcessorStep(SharedProcessorIds.MYBATIS_RUNTIME,
                        GenerationStage.POST_WRITE, 300, FailurePolicy.CONTINUE));
    }

    private static List<FileBlueprint> fileBlueprints(
            CrudTemplateModel model, String domain, String outputPath, String pkgSub,
            CrudViewType viewType, CrudLayoutMode layoutMode,
            ThymeleafLayoutValidator.LayoutReference layoutReference) {

        List<FileBlueprint> files = new ArrayList<>();
        for (CrudLayerDefinition layer : CrudLayerDefinition.forViewType(viewType)) {
            if (viewType == CrudViewType.THYMELEAF
                    && CrudLayerDefinition.isLayoutLayer(layer.layerKey())
                    && layoutMode != CrudLayoutMode.CREATE) {
                continue;
            }
            String fileName = CrudLayerDefinition.resolveFileName(
                    layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath = layer.resolveSubPath(pkgSub, model.domainLc());
            files.add(new FileBlueprint(
                    layer.layerKey(), fileName,
                    Path.of(outputPath + "/" + subPath + fileName),
                    new CrudRenderRequest(layer.layerKey(), model, viewType, layoutReference, layoutMode)));
        }
        return files;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** APR-B04: 승인 정책으로 차단된 시도도 다른 실패 사유와 동일하게 감사 이력에 남긴다. */
    private void recordApprovalPolicyBlocked(String outputPath, String tableName, String viewType) {
        String operationId = CrudGenerationOperationIdFactory.forScreen(outputPath, tableName, viewType);
        var context = ObservabilityContextHolder.current();
        try {
            auditPort.append(new GenerationAuditRecord(
                    UUID.randomUUID().toString(), operationId, 0, outputPath, tableName, null,
                    context.channel(), context.actorId(),
                    System.getProperty("spring.profiles.active", "UNKNOWN"),
                    List.of(), List.of(), List.of(), List.of(),
                    GenerationOperationStatus.REJECTED, "approval-policy",
                    "고위험 테이블 정책: 승인된 화면명세 없이 생성 시도 차단", Instant.now()));
        } catch (RuntimeException exception) {
            log.error("[plan] 승인 정책 차단 감사 이력 저장 실패: operationId={}", operationId, exception);
        }
    }

    private static boolean detailSubsetRequested(ScreenSpecification screenSpecification) {
        return screenSpecification != null
                && screenSpecification.pages().stream()
                        .filter(page -> "detail".equalsIgnoreCase(page.id()))
                        .findFirst()
                        .map(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                        .orElse(false);
    }

    private static boolean screenFieldSubsetRequested(ScreenSpecification screenSpecification) {
        return screenSpecification != null
                && screenSpecification.pages().stream()
                        .anyMatch(page -> page.selectionSource() != FieldSelectionSource.DEFAULT);
    }

    private static RendererCapabilityRequirement rendererCapabilityRequirement(
            CrudTemplateModel model, ScreenSpecification screenSpecification, CrudLayoutMode layoutMode) {
        Set<RendererFeature> features = new LinkedHashSet<>(Set.of(
                RendererFeature.CRUD_LIST,
                RendererFeature.CRUD_DETAIL,
                RendererFeature.CRUD_CREATE,
                RendererFeature.CRUD_UPDATE,
                RendererFeature.CRUD_SEARCH));
        if (model.pkFields() != null && model.pkFields().size() > 1) {
            features.add(RendererFeature.COMPOSITE_PRIMARY_KEY);
        }
        if (screenFieldSubsetRequested(screenSpecification)) {
            features.add(RendererFeature.SCREEN_FIELD_SUBSET);
        }
        if (layoutMode != CrudLayoutMode.NONE) {
            features.add(RendererFeature.LAYOUT_DECORATION);
        }
        return new RendererCapabilityRequirement(features, Set.<RendererFallback>of());
    }

    private static CrudGenerationPlan rendererCapabilityFailure(
            CrudProgramMetadata metadata, CrudTemplateModel model, List<String> issues,
            List<String> warnings) {
        return CrudGenerationPlan.rejected(new CrudPlanFailure(
                CrudPlanFailure.Kind.RENDERER_CAPABILITY_BLOCKED,
                "Renderer Capability 검증 실패", issues,
                metadata.menuIntegrationStatus(), metadata.programKoreanName(), null,
                model.route().canonicalListPath(), warnings));
    }

    /** route의 모든 alias(role별 GET/POST)를 대상으로 기존 Controller와의 충돌을 검사한다. */
    private Map<String, String> checkAliasConflicts(
            CrudTemplateModel model, String domain, String outputPath,
            String pkgSub, CrudViewType viewType) {
        var route = model.route();
        String targetPath = CrudLayerDefinition.forViewType(viewType).stream()
                .filter(layer -> "controller".equals(layer.layerKey()))
                .findFirst()
                .map(layer -> outputPath + "/" + layer.resolveSubPath(pkgSub, model.domainLc())
                        + CrudLayerDefinition.resolveFileName("controller", domain, layer.fileNameSuffix()))
                .orElse("Egov" + domain + "Controller.java");
        Map<String, String> conflicts = new LinkedHashMap<>();
        record AliasCheck(boolean present, String path, String method) {}
        List<AliasCheck> checks = List.of(
                new AliasCheck(route.hasListAlias(), route.registeredListPath(), "GET"),
                new AliasCheck(route.hasDetailAlias(), route.registeredDetailPath(), "GET"),
                new AliasCheck(route.hasRegistViewAlias(), route.registeredRegistViewPath(), "GET"),
                new AliasCheck(route.hasUpdtViewAlias(), route.registeredUpdtViewPath(), "GET"),
                new AliasCheck(route.hasRegistAlias(), route.registeredRegistPath(), "POST"),
                new AliasCheck(route.hasUpdtAlias(), route.registeredUpdtPath(), "POST"),
                new AliasCheck(route.hasDeleteAlias(), route.registeredDeletePath(), "POST")
        );
        for (AliasCheck check : checks) {
            if (!check.present()) continue;
            List<String> found = routeCollisionDetector.findConflicts(
                    outputPath, check.path(), check.method(), targetPath);
            if (!found.isEmpty()) conflicts.put(check.path(), String.join(", ", found));
        }
        return conflicts;
    }
}
