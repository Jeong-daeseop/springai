package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.design.ScreenSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * eGovFrame 게시판(BBS) 업무 단위 소스를 결정적으로 생성·저장하는 오케스트레이터.
 *
 * <p>게시글/마스터/사용권한/첨부파일 연동 + 목록/상세/등록/수정/논리삭제 + 조회수 증가를
 * FreeMarker 템플릿 렌더링으로 한 번에 생성한다 (LLM 미개입).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardOrchestrationService {

    private final BoardSchemaService boardSchemaService;
    private final BoardModelFactory boardModelFactory;
    private final BoardTemplateRenderer boardTemplateRenderer;
    private final CodeService codeService;
    private final CodeValidatorService codeValidatorService;
    private final GenerationHistoryService generationHistoryService;
    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final BoardTableSetResolver boardTableSetResolver;
    private final BoardProgramMetadataService boardProgramMetadataService;
    private final BoardRouteCollisionDetector boardRouteCollisionDetector;
    private final KrdsStylesConfigurer krdsStylesConfigurer;
    private final BoardGeneratedCodeAuditor boardGeneratedCodeAuditor;
    private final GenerationDesignContextService generationDesignContextService;
    private final GeneratedCodeContractAuditor generatedCodeContractAuditor;
    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    private final WarEntryPointConfigurer warEntryPointConfigurer;

    public BoardOrchestrationResult orchestrate(
            String database,
            String domain, String packageName, String outputPath,
            String mainTable, String masterTable, String useTable,
            String fileTable, String fileDetailTable,
            String egovVersion, String viewType) {
        return orchestrate(database, domain, packageName, outputPath,
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType, null, null, null, BoardGenerationOptions.empty());
    }

    public BoardOrchestrationResult orchestrate(
            String database,
            String domain, String packageName, String outputPath,
            String mainTable, String masterTable, String useTable,
            String fileTable, String fileDetailTable,
            String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView) {
        return orchestrate(database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, layoutMode, layoutView,
                breadcrumbView, BoardGenerationOptions.empty());
    }

    public BoardOrchestrationResult orchestrate(
            String database,
            String domain, String packageName, String outputPath,
            String mainTable, String masterTable, String useTable,
            String fileTable, String fileDetailTable,
            String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView,
            BoardGenerationOptions options) {

        BoardTableSet tables = boardTableSetResolver.resolve(
                database, mainTable, masterTable, useTable, fileTable, fileDetailTable);
        mainTable = tables.mainTable();
        masterTable = tables.masterTable();
        useTable = tables.useTable();
        fileTable = tables.fileTable();
        fileDetailTable = tables.fileDetailTable();

        log.info("[board-orchestrate] 시작: mainTable={}, domain={}, viewType={}", mainTable, domain, viewType);

        // packageName 검증
        if (packageName == null || !packageName.startsWith("egovframework.let.")) {
            throw new IllegalArgumentException(
                "packageName은 egovframework.let.* 형식이어야 합니다: " + packageName);
        }

        // 1. 스키마 조회
        Map<String, List<Map<String, Object>>> schemas;
        try {
            schemas = boardSchemaService.fetchBoardSchemas(
                database, mainTable, masterTable, useTable, fileTable, fileDetailTable);
        } catch (IllegalArgumentException e) {
            log.warn("[board-orchestrate] 필수 테이블 없음: {}", e.getMessage());
            return BoardOrchestrationResult.notFound(database, mainTable);
        }

        BoardProgramMetadata metadata = boardProgramMetadataService.resolve(
                database, domain, masterTable, options);
        List<String> warnings = new ArrayList<>();
        if (metadata.message() != null) warnings.add(metadata.message());
        if (metadata.blocksGeneration()) {
            return new BoardOrchestrationResult(false, database, mainTable, domain, outputPath,
                    List.of(), List.of(metadata.message()), "메타데이터 검증 실패", "",
                    metadata.menuIntegrationStatus(), metadata.programKoreanName(),
                    metadata.registeredUrl(), null, metadata.defaultBbsId(), "미실행", warnings);
        }

        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                database, mainTable, metadata.programKoreanName(), "board",
                options == null ? null : options.designReferenceId(),
                options == null ? null : options.screenSpecificationId());

        // 2. 모델 생성
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        CrudLayoutMode resolvedLayoutMode = resolvedViewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(layoutMode)
                : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference layoutReference = resolvedViewType == CrudViewType.THYMELEAF
                ? thymeleafLayoutValidator.resolve(layoutView, breadcrumbView)
                : thymeleafLayoutValidator.resolve(null, null);
        BoardTemplateModel model = screenSpecification == null
                ? boardModelFactory.fromSchemas(
                        mainTable, masterTable, useTable, fileDetailTable,
                        domain, packageName, egovVersion, schemas, metadata)
                : boardModelFactory.fromSchemas(
                        mainTable, masterTable, useTable, fileDetailTable,
                        domain, packageName, egovVersion, schemas, metadata, screenSpecification);

        List<String> aliasConflicts = boardRouteCollisionDetector.findConflicts(
                outputPath, model.route().hasListAlias() ? model.route().registeredListPath() : null,
                "Egov" + domain + "Controller.java");
        if (!aliasConflicts.isEmpty()) {
            String message = "Controller URL alias 충돌(ambiguous mapping 위험): "
                    + model.route().registeredListPath() + " — " + aliasConflicts;
            return new BoardOrchestrationResult(false, database, mainTable, domain, outputPath,
                    List.of(), List.of(message), "URL 검증 실패", "",
                    metadata.menuIntegrationStatus(), model.display().displayName(),
                    metadata.registeredUrl(), model.urlPrefix() + "List.do",
                    metadata.defaultBbsId(), "미실행", warnings);
        }

        if (resolvedViewType == CrudViewType.THYMELEAF && resolvedLayoutMode == CrudLayoutMode.REUSE) {
            ThymeleafLayoutValidator.LayoutValidationResult validation =
                    thymeleafLayoutValidator.validateExisting(outputPath, layoutReference.layoutView(), layoutReference.breadcrumbView());
            if (!validation.valid()) {
                String message = thymeleafLayoutValidator.missingLayoutMessage(outputPath, validation);
                return new BoardOrchestrationResult(false, database, mainTable, domain, outputPath,
                        List.of(), List.of(message), "layout 검증 실패", "",
                        metadata.menuIntegrationStatus(), model.display().displayName(),
                        metadata.registeredUrl(), model.urlPrefix() + "List.do",
                        metadata.defaultBbsId(), "미실행", warnings);
            }
        }

        // 3. 레이어 렌더링 + 저장
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (BoardLayerDefinition layer : BoardLayerDefinition.forViewType(resolvedViewType)) {
            if (resolvedViewType == CrudViewType.THYMELEAF
                    && BoardLayerDefinition.isLayoutLayer(layer.layerKey())
                    && resolvedLayoutMode != CrudLayoutMode.CREATE) {
                continue;
            }
            String fileName = BoardLayerDefinition.resolveFileName(
                layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath = layer.resolveSubPath(pkgSub, model.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code = resolvedViewType == CrudViewType.THYMELEAF
                        ? boardTemplateRenderer.renderByLayerKey(
                                layer.layerKey(), model,
                                layoutReference.layoutView(),
                                layoutReference.breadcrumbView(),
                                layoutReference.layoutBasePath(),
                                resolvedLayoutMode)
                        : boardTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
                String saveResult = codeService.saveGeneratedCode(filePath, code);
                if (saveResult.startsWith("파일 저장 실패")) {
                    failed.add(fileName + " — " + saveResult);
                } else {
                    succeeded.add(fileName);
                    log.info("[board-orchestrate] 저장: {}", filePath);
                }
            } catch (Exception e) {
                failed.add(fileName + " — 오류: " + e.getMessage());
                log.error("[board-orchestrate] 실패: layer={}, error={}", layer.layerKey(), e.getMessage());
            }
        }

        // Thymeleaf 런타임 보강
        String cssStatus = "JSP 생성 — CSS 보강 미실행";
        if (resolvedViewType == CrudViewType.THYMELEAF) {
            thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath, egovVersion, failed);
            thymeleafRuntimeConfigurer.ensureControllerComponentScan(
                    outputPath, model.packageName() + ".web", failed);
            KrdsStylesConfigurer.CssPatchResult css = krdsStylesConfigurer.ensureBoardCrudStyles(outputPath);
            cssStatus = css.status() + " — " + css.message();
            if (css.failed()) failed.add("styles.css — " + css.message());
        }
        MyBatisRuntimeConfigurer.ConfigurationResult myBatis =
                myBatisRuntimeConfigurer.ensureConfigured(
                        outputPath, model.packageName() + ".service.impl");
        if (!myBatis.success()) {
            failed.add("context-common.xml — " + myBatis.message());
        }
        updateDefaultIndexForward(outputPath, model, resolvedViewType, succeeded, failed);

        // 4. 검증
        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
        }
        String boardAudit = boardGeneratedCodeAuditor.audit(outputPath, model, resolvedViewType,
                layoutReference.layoutBasePath());
        List<String> contractFailures = generatedCodeContractAuditor.audit(outputPath);
        if (!contractFailures.isEmpty()) {
            failed.addAll(contractFailures.stream().map(value -> "생성 계약 감사 — " + value).toList());
            validationSummary += "\n\n[공통 생성 계약 감사]\n" + String.join("\n", contractFailures);
        }
        validationSummary += "\n\n[게시판 생성 감사]\n" + boardAudit;
        if (boardAudit.startsWith("감사 실패")) {
            failed.add("게시판 생성 감사 — " + boardAudit);
        }

        // 5. 이력
        String historySummary;
        try {
            historySummary = generationHistoryService.saveHistory(
                mainTable, domain, packageName, outputPath, succeeded.size() + "개 파일");
        } catch (Exception e) {
            historySummary = "이력 저장 실패: " + e.getMessage();
        }

        log.info("[board-orchestrate] 완료: success={}, fail={}", succeeded.size(), failed.size());
        return new BoardOrchestrationResult(
            false, database, mainTable, domain, outputPath,
            succeeded, failed, validationSummary, historySummary,
            metadata.menuIntegrationStatus(), model.display().displayName(),
            metadata.registeredUrl(), model.urlPrefix() + "List.do",
            metadata.defaultBbsId(), cssStatus, warnings);
    }

    private void updateDefaultIndexForward(
            String outputPath, BoardTemplateModel model, CrudViewType viewType,
            List<String> succeeded, List<String> failed) {
        String listViewName = "Egov" + model.domain() + "List"
                + (viewType == CrudViewType.THYMELEAF ? ".html" : ".jsp");
        if (!succeeded.contains(listViewName)) {
            log.info("[board-orchestrate] 목록 화면 저장 전이므로 index.jsp 기본 진입점 갱신 생략: {}", listViewName);
            return;
        }

        String listUrl = model.urlPrefix() + "List.do";
        if (model.route().defaultBbsId() != null) {
            listUrl += "?bbsId=" + model.route().defaultBbsId();
        }
        WarEntryPointConfigurer.ConfigurationResult result =
                warEntryPointConfigurer.configure(outputPath, listUrl);
        if (!result.success()) {
            failed.add("WAR 기본 진입점 — " + result.message());
            log.error("[board-orchestrate] WAR 기본 진입점 갱신 실패: {}", result.message());
        } else {
            log.info("[board-orchestrate] WAR 기본 진입점 갱신 완료: {}", result.message());
        }
    }
}
