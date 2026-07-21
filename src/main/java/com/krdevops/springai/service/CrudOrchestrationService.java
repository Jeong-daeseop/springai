package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.ScreenSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * eGovFrame CRUD 레이어 소스를 결정적으로 생성·저장하는 오케스트레이터 (JSP: 11개, Thymeleaf: 16개).
 *
 * <p>LLM 미개입 — FreeMarker 템플릿 렌더링으로 처리하므로 Claude 토큰을 대폭 절감한다.
 * 기존 {@code CrudPromptBuilderTool.orchestrateAuto()}의 로직을 Service 레이어로 이전하여
 * Tool 레이어를 얇게(thin) 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrudOrchestrationService {

    private final CrudSchemaQueryService   crudSchemaQueryService;
    private final CrudModelFactory         crudModelFactory;
    private final CrudTemplateRenderer     crudTemplateRenderer;
    private final CodeService              codeService;
    private final CodeValidatorService     codeValidatorService;
    private final GenerationHistoryService generationHistoryService;
    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final CrudProgramMetadataService crudProgramMetadataService;
    private final BoardRouteCollisionDetector routeCollisionDetector;
    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    private final WarEntryPointConfigurer warEntryPointConfigurer;
    private final GenerationDesignContextService generationDesignContextService;
    private final GeneratedCodeContractAuditor generatedCodeContractAuditor;
    private final KrdsStylesConfigurer krdsStylesConfigurer;

    /**
     * 지정 테이블의 CRUD 소스를 viewType별 레이어 수(JSP: 11개, Thymeleaf: 16개)만큼 생성·저장하고 결과를 반환한다.
     *
     * <p>테이블 미존재 시 예외 대신 {@link CrudOrchestrationResult#notFound}를 반환한다.
     * Tool 레이어는 이 결과를 문자열로 포맷팅하여 MCP 응답을 구성한다.
     */
    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, "jsp");
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                null, null, null);
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                layoutMode, layoutView, breadcrumbView, CrudGenerationOptions.empty());
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView,
            CrudGenerationOptions options) {

        CrudGenerationOptions resolvedOptions = options == null ? CrudGenerationOptions.empty() : options;

        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        CrudLayoutMode resolvedLayoutMode = resolvedViewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(layoutMode)
                : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference layoutReference = resolvedViewType == CrudViewType.THYMELEAF
                ? thymeleafLayoutValidator.resolve(layoutView, breadcrumbView)
                : thymeleafLayoutValidator.resolve(null, null);
        log.info("[orchestrate] 시작: table={}, domain={}, outputPath={}, egovVersion={}, viewType={}",
                 tableName, domain, outputPath, egovVersion, resolvedViewType.value());

        // 1. 스키마 조회
        List<Map<String, Object>> rawColumns =
                crudSchemaQueryService.fetchColumns(database, tableName);
        if (rawColumns.isEmpty()) {
            log.warn("[orchestrate] 테이블 없음: {}.{}", database, tableName);
            return CrudOrchestrationResult.notFound(database, tableName);
        }

        // 2. FreeMarker 모델 생성
        // ⚠️ CrudLayerDefinition 템플릿은 egovframework/let/{PKG}/... 고정이므로
        //    packageName이 egovframework.let.* 형식이 아니면 경로 오계산 발생 — 조기 실패 처리
        if (packageName == null || !packageName.startsWith("egovframework.let.")) {
            throw new IllegalArgumentException(
                "packageName은 egovframework.let.* 형식이어야 합니다: " + packageName);
        }
        String pkgSub = packageName
                .replace("egovframework.let.", "").replace(".", "/");

        CrudProgramMetadata metadata = crudProgramMetadataService.resolve(database, domain, tableName, resolvedOptions);
        List<String> warnings = new ArrayList<>();
        if (metadata.message() != null) warnings.add(metadata.message());
        if (metadata.blocksGeneration()) {
            return new CrudOrchestrationResult(false, database, tableName, domain, outputPath,
                    List.of(), List.of(metadata.message()), "메타데이터 검증 실패", "",
                    metadata.menuIntegrationStatus(), metadata.programKoreanName(), null, null, warnings);
        }

        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                database, tableName, metadata.programKoreanName(), "crud",
                resolvedOptions.designReferenceId(), resolvedOptions.screenSpecificationId());
        ScreenSubsetMode subsetMode = resolvedViewType == CrudViewType.THYMELEAF
                ? ScreenSubsetMode.LIST_AND_DETAIL : ScreenSubsetMode.LIST_ONLY;
        CrudTemplateModel model = crudModelFactory.fromSchema(
                tableName, domain, packageName, egovVersion, rawColumns, metadata,
                resolvedViewType, subsetMode, screenSpecification);

        boolean detailSubsetRequested = screenSpecification != null
                && screenSpecification.pages().stream()
                        .filter(page -> "detail".equalsIgnoreCase(page.id()))
                        .findFirst()
                        .map(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                        .orElse(false);
        if (resolvedViewType == CrudViewType.JSP && detailSubsetRequested) {
            warnings.add("JSP 생성에서는 detail 화면 필드 subset을 지원하지 않아 표준 상세 필드를 사용합니다.");
        }

        Map<String, String> aliasConflicts = checkAliasConflicts(
                model, domain, outputPath, pkgSub, resolvedViewType);
        if (!aliasConflicts.isEmpty()) {
            String message = "Controller URL alias 충돌(ambiguous mapping 위험): " + aliasConflicts;
            return new CrudOrchestrationResult(false, database, tableName, domain, outputPath,
                    List.of(), List.of(message), "URL 검증 실패", "",
                    metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                    model.route().registeredListPath(), model.route().canonicalListPath(), warnings);
        }

        if (resolvedViewType == CrudViewType.THYMELEAF && resolvedLayoutMode == CrudLayoutMode.REUSE) {
            ThymeleafLayoutValidator.LayoutValidationResult validation =
                    thymeleafLayoutValidator.validateExisting(outputPath, layoutReference.layoutView(), layoutReference.breadcrumbView());
            if (!validation.valid()) {
                String message = thymeleafLayoutValidator.missingLayoutMessage(outputPath, validation);
                return new CrudOrchestrationResult(false, database, tableName, domain, outputPath,
                        List.of(), List.of(message), "layout 검증 실패", "");
            }
        }

        if (model.layoutDensity() != LayoutDensity.STANDARD) {
            KrdsStylesConfigurer.CssPatchResult css =
                    krdsStylesConfigurer.ensureTableDensityStyles(outputPath);
            if (css.failed()) {
                return new CrudOrchestrationResult(false, database, tableName, domain, outputPath,
                        List.of(), List.of("styles.css — " + css.message()), "CSS 보강 실패", "",
                        metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                        model.route().hasListAlias() ? model.route().registeredListPath() : null,
                        model.route().canonicalListPath(), warnings);
            }
        }

        if (model.formColumnLayout() != FormColumnLayout.SINGLE_COLUMN) {
            KrdsStylesConfigurer.CssPatchResult css =
                    krdsStylesConfigurer.ensureFormColumnLayoutStyles(outputPath);
            if (css.failed()) {
                return new CrudOrchestrationResult(false, database, tableName, domain, outputPath,
                        List.of(), List.of("styles.css — " + css.message()), "CSS 보강 실패", "",
                        metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                        model.route().hasListAlias() ? model.route().registeredListPath() : null,
                        model.route().canonicalListPath(), warnings);
            }
        }

        // 3. viewType별 CrudLayerDefinition 기준으로 렌더링 + 저장
        List<String> succeeded = new ArrayList<>();
        List<String> failed    = new ArrayList<>();

        for (CrudLayerDefinition layer : CrudLayerDefinition.forViewType(resolvedViewType)) {
            if (resolvedViewType == CrudViewType.THYMELEAF
                    && CrudLayerDefinition.isLayoutLayer(layer.layerKey())
                    && resolvedLayoutMode != CrudLayoutMode.CREATE) {
                continue;
            }
            String fileName = CrudLayerDefinition.resolveFileName(
                    layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath  = layer.resolveSubPath(pkgSub, model.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code = resolvedViewType == CrudViewType.THYMELEAF
                        ? crudTemplateRenderer.renderByLayerKey(
                                layer.layerKey(), model,
                                layoutReference.layoutView(),
                                layoutReference.breadcrumbView(),
                                layoutReference.layoutBasePath(),
                                resolvedLayoutMode)
                        : crudTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
                String saveResult = codeService.saveGeneratedCode(filePath, code);
                if (saveResult.startsWith("파일 저장 실패")) {
                    failed.add(fileName + " — " + saveResult);
                    log.error("[orchestrate] 저장 실패: {}", filePath);
                } else {
                    succeeded.add(fileName);
                    log.info("[orchestrate] 저장 완료: {}", filePath);
                }
            } catch (Exception e) {
                failed.add(fileName + " — 오류: " + e.getMessage());
                log.error("[orchestrate] 렌더링/저장 실패: layer={}, error={}", layer.layerKey(), e.getMessage());
            }
        }

        updateDefaultIndexForward(outputPath, model, resolvedViewType, succeeded, failed);
        if (resolvedViewType == CrudViewType.THYMELEAF) {
            thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath, egovVersion, failed);
            thymeleafRuntimeConfigurer.ensureControllerComponentScan(
                    outputPath, model.packageName() + ".web", failed);
        }
        MyBatisRuntimeConfigurer.ConfigurationResult myBatis =
                myBatisRuntimeConfigurer.ensureConfigured(
                        outputPath, model.packageName() + ".service.impl");
        if (!myBatis.success()) {
            failed.add("context-common.xml — " + myBatis.message());
        }

        // 4. 코드 검증
        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
            log.warn("[orchestrate] 코드 검증 실패: {}", e.getMessage());
        }
        List<String> contractFailures = generatedCodeContractAuditor.audit(outputPath);
        if (!contractFailures.isEmpty()) {
            failed.addAll(contractFailures.stream().map(value -> "생성 계약 감사 — " + value).toList());
            validationSummary += "\n\n[생성 계약 감사]\n" + String.join("\n", contractFailures);
        }

        // 5. 생성 이력
        String historySummary;
        try {
            historySummary = generationHistoryService.saveHistory(
                    tableName, domain, packageName, outputPath, succeeded.size() + "개 파일");
        } catch (Exception e) {
            historySummary = "생성 이력 저장 실패: " + e.getMessage();
            log.warn("[orchestrate] 생성 이력 저장 실패: {}", e.getMessage());
        }

        log.info("[orchestrate] 완료: successCount={}, failCount={}", succeeded.size(), failed.size());
        return new CrudOrchestrationResult(
                false, database, tableName, domain, outputPath,
                succeeded, failed, validationSummary, historySummary,
                metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                model.route().hasListAlias() ? model.route().registeredListPath() : null,
                model.route().canonicalListPath(), warnings);
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
        Map<String, String> conflicts = new java.util.LinkedHashMap<>();
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

    private void updateDefaultIndexForward(
            String outputPath, CrudTemplateModel model, CrudViewType viewType,
            List<String> succeeded, List<String> failed) {
        String listViewName = "Egov" + model.domain() + "List"
                + (viewType == CrudViewType.THYMELEAF ? ".html" : ".jsp");
        if (!succeeded.contains(listViewName)) {
            log.info("[orchestrate] 목록 화면 저장 전이므로 index.jsp 기본 진입점 갱신 생략: {}", listViewName);
            return;
        }

        String listUrl = model.route().resolvedListPath();
        WarEntryPointConfigurer.ConfigurationResult result =
                warEntryPointConfigurer.configure(outputPath, listUrl);
        if (!result.success()) {
            failed.add("WAR 기본 진입점 — " + result.message());
            log.error("[orchestrate] WAR 기본 진입점 갱신 실패: {}", result.message());
        } else {
            log.info("[orchestrate] WAR 기본 진입점 갱신 완료: {}", result.message());
        }
    }
}
