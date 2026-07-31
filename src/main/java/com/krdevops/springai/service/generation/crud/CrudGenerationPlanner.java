package com.krdevops.springai.service.generation.crud;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@RequiredArgsConstructor
public class CrudGenerationPlanner {

    private static final String PACKAGE_PREFIX = "egovframework.let.";

    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudProgramMetadataService crudProgramMetadataService;
    private final GenerationDesignContextService generationDesignContextService;
    private final CrudModelFactory crudModelFactory;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final BoardRouteCollisionDetector routeCollisionDetector;

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
        ScreenSubsetMode subsetMode = viewType == CrudViewType.THYMELEAF
                ? ScreenSubsetMode.LIST_AND_DETAIL : ScreenSubsetMode.LIST_ONLY;
        CrudTemplateModel model = crudModelFactory.fromSchema(
                tableName, domain, packageName, egovVersion, rawColumns, metadata,
                viewType, subsetMode, screenSpecification);

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
                viewType.value(), attributes(model, viewType, layoutMode, layoutReference));

        GenerationBlueprint blueprint = new GenerationBlueprint(
                context,
                fileBlueprints(model, domain, outputPath, pkgSub, viewType, layoutMode, layoutReference),
                processorSteps(),
                warnings.stream().map(GenerationWarning::new).toList());
        return new CrudGenerationPlan(blueprint, null, metadata, model, warnings);
    }

    private static Map<String, Object> attributes(
            CrudTemplateModel model, CrudViewType viewType, CrudLayoutMode layoutMode,
            ThymeleafLayoutValidator.LayoutReference layoutReference) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(CrudGenerationAttributes.MODEL, model);
        attributes.put(CrudGenerationAttributes.VIEW_TYPE, viewType);
        attributes.put(CrudGenerationAttributes.LAYOUT_MODE, layoutMode);
        attributes.put(CrudGenerationAttributes.LAYOUT_REFERENCE, layoutReference);
        return attributes;
    }

    /**
     * WP-0 {@code CrudOrchestrationProcessorOrderTest}가 실측한 실제 실행 순서를 선언한다.
     * Directory 검증/Contract 감사는 {@code GenerationVerifier}로 분리되어 있어 여기 없다.
     */
    private static List<ProcessorStep> processorSteps() {
        return List.of(
                new ProcessorStep(CrudTableDensityCssProcessor.ID,
                        GenerationStage.PRE_WRITE, 100, FailurePolicy.STOP),
                new ProcessorStep(CrudFormColumnCssProcessor.ID,
                        GenerationStage.PRE_WRITE, 110, FailurePolicy.STOP),
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

    private static boolean detailSubsetRequested(ScreenSpecification screenSpecification) {
        return screenSpecification != null
                && screenSpecification.pages().stream()
                        .filter(page -> "detail".equalsIgnoreCase(page.id()))
                        .findFirst()
                        .map(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                        .orElse(false);
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
