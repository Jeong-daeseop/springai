package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.util.CrudMappingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDetailOrchestrationService {

    private final CrudSchemaQueryService crudSchemaQueryService;
    private final CrudModelFactory crudModelFactory;
    private final MasterDetailTemplateRenderer masterDetailTemplateRenderer;
    private final CodeService codeService;
    private final CodeValidatorService codeValidatorService;
    private final GenerationHistoryService generationHistoryService;
    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;

    public MasterDetailOrchestrationResult orchestrate(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            String egovVersion,
            String viewType) {
        return orchestrate(database, masterTable, detailTable, domain, packageName,
                outputPath, egovVersion, viewType, null, null, null);
    }

    public MasterDetailOrchestrationResult orchestrate(
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            String egovVersion,
            String viewType,
            String layoutMode,
            String layoutView,
            String breadcrumbView) {

        log.info("[master-detail-orchestrate] 시작: master={}, detail={}, domain={}, viewType={}",
                masterTable, detailTable, domain, viewType);

        if (packageName == null || !packageName.startsWith("egovframework.let.")) {
            throw new IllegalArgumentException(
                    "packageName은 egovframework.let.* 형식이어야 합니다: " + packageName);
        }

        List<Map<String, Object>> masterColumns = crudSchemaQueryService.fetchColumns(database, masterTable);
        List<Map<String, Object>> detailColumns = crudSchemaQueryService.fetchColumns(database, detailTable);
        if (masterColumns.isEmpty() || detailColumns.isEmpty()) {
            log.warn("[master-detail-orchestrate] 테이블 없음: master={}, detail={}", masterTable, detailTable);
            return MasterDetailOrchestrationResult.notFound(database, masterTable, detailTable);
        }

        String detailDomain = deriveDetailDomain(detailTable);
        CrudTemplateModel masterModel =
                crudModelFactory.fromSchema(masterTable, domain, packageName, egovVersion, masterColumns);
        CrudTemplateModel detailModel =
                crudModelFactory.fromSchema(detailTable, detailDomain, packageName, egovVersion, detailColumns);
        String fkColumn = detectFkColumn(masterModel.pk().columnName(), detailColumns);
        String fkField = CrudMappingUtils.toCamelCase(fkColumn);
        MasterDetailTemplateModel model = new MasterDetailTemplateModel(
                masterModel, detailModel, fkColumn, fkField);

        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        CrudLayoutMode resolvedLayoutMode = resolvedViewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(layoutMode)
                : CrudLayoutMode.CREATE;
        ThymeleafLayoutValidator.LayoutReference layoutReference = resolvedViewType == CrudViewType.THYMELEAF
                ? thymeleafLayoutValidator.resolve(layoutView, breadcrumbView)
                : thymeleafLayoutValidator.resolve(null, null);

        if (resolvedViewType == CrudViewType.THYMELEAF && resolvedLayoutMode == CrudLayoutMode.REUSE) {
            ThymeleafLayoutValidator.LayoutValidationResult validation =
                    thymeleafLayoutValidator.validateExisting(outputPath, layoutReference.layoutView(), layoutReference.breadcrumbView());
            if (!validation.valid()) {
                String message = thymeleafLayoutValidator.missingLayoutMessage(outputPath, validation);
                return new MasterDetailOrchestrationResult(false, database, masterTable, detailTable, domain, outputPath,
                        List.of(), List.of(message), "layout 검증 실패", "");
            }
        }
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (MasterDetailLayerDefinition layer : MasterDetailLayerDefinition.forViewType(resolvedViewType)) {
            if (resolvedViewType == CrudViewType.THYMELEAF
                    && MasterDetailLayerDefinition.isLayoutLayer(layer.layerKey())
                    && resolvedLayoutMode != CrudLayoutMode.CREATE) {
                continue;
            }
            String fileName = MasterDetailLayerDefinition.resolveFileName(layer, domain, detailDomain);
            String subPath = layer.resolveSubPath(pkgSub, masterModel.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code = resolvedViewType == CrudViewType.THYMELEAF
                        ? masterDetailTemplateRenderer.renderByLayerKey(
                                layer.layerKey(), model,
                                layoutReference.layoutView(),
                                layoutReference.breadcrumbView(),
                                layoutReference.layoutBasePath(),
                                resolvedLayoutMode)
                        : masterDetailTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
                String saveResult = codeService.saveGeneratedCode(filePath, code);
                if (saveResult.startsWith("파일 저장 실패")) {
                    failed.add(fileName + " — " + saveResult);
                    log.error("[master-detail-orchestrate] 저장 실패: {}", filePath);
                } else {
                    succeeded.add(fileName);
                    log.info("[master-detail-orchestrate] 저장 완료: {}", filePath);
                }
            } catch (Exception e) {
                failed.add(fileName + " — 오류: " + e.getMessage());
                log.error("[master-detail-orchestrate] 렌더링/저장 실패: layer={}, error={}",
                        layer.layerKey(), e.getMessage());
            }
        }

        if (resolvedViewType == CrudViewType.THYMELEAF) {
            thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath, egovVersion, failed);
        }
        updateDefaultMainController(outputPath, masterModel, succeeded, failed);
        ensureServletContextScansCom(outputPath, failed);

        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
            log.warn("[master-detail-orchestrate] 코드 검증 실패: {}", e.getMessage());
        }

        String historySummary;
        try {
            historySummary = generationHistoryService.saveHistory(
                    masterTable + "+" + detailTable, domain, packageName, outputPath,
                    succeeded.size() + "개 파일");
        } catch (Exception e) {
            historySummary = "생성 이력 저장 실패: " + e.getMessage();
            log.warn("[master-detail-orchestrate] 생성 이력 저장 실패: {}", e.getMessage());
        }

        log.info("[master-detail-orchestrate] 완료: success={}, fail={}", succeeded.size(), failed.size());
        return new MasterDetailOrchestrationResult(
                false, database, masterTable, detailTable, domain, outputPath,
                succeeded, failed, validationSummary, historySummary);
    }

    private String detectFkColumn(String masterPkColumn, List<Map<String, Object>> detailColumns) {
        return detailColumns.stream()
                .map(c -> (String) c.get("COLUMN_NAME"))
                .filter(masterPkColumn::equals)
                .findFirst()
                .orElse(masterPkColumn);
    }

    private void updateDefaultMainController(
            String outputPath, CrudTemplateModel model, List<String> succeeded, List<String> failed) {
        String controllerPath = outputPath
                + "/src/main/java/egovframework/com/web/EgovMainController.java";
        String listUrl = model.urlPrefix() + "List.do";
        String controller = """
package egovframework.com.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 화면 Controller
 */
@Controller
public class EgovMainController {

    @GetMapping("/egovframework/com/main.do")
    public String main() {
        return "redirect:%s";
    }
}
""".formatted(listUrl);

        String saveResult = codeService.saveGeneratedCode(controllerPath, controller);
        if (saveResult == null || saveResult.startsWith("파일 저장 실패")) {
            failed.add("EgovMainController.java — " + saveResult);
            log.error("[master-detail-orchestrate] EgovMainController 생성 실패: {}", controllerPath);
        } else {
            succeeded.add("EgovMainController.java");
            log.info("[master-detail-orchestrate] EgovMainController 생성 완료: {} -> {}", controllerPath, listUrl);
        }
    }

    private void ensureServletContextScansCom(String outputPath, List<String> failed) {
        Path servletContextPath = Path.of(outputPath,
                "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        if (!Files.exists(servletContextPath)) {
            log.info("[master-detail-orchestrate] servlet-context.xml 없음, component-scan 보강 생략: {}",
                    servletContextPath);
            return;
        }
        try {
            String content = Files.readString(servletContextPath, StandardCharsets.UTF_8);
            if (content.contains("egovframework.com")) {
                log.info("[master-detail-orchestrate] servlet-context.xml component-scan 이미 보강됨: {}",
                        servletContextPath);
                return;
            }

            String updated = content.replaceFirst(
                    "base-package=\"([^\"]*egovframework\\.let[^\"]*)\"",
                    "base-package=\"$1,egovframework.com\"");
            if (updated.equals(content)) {
                failed.add("servlet-context.xml — component-scan base-package 패턴을 찾을 수 없습니다.");
                log.warn("[master-detail-orchestrate] servlet-context.xml component-scan 패턴 미탐지: {}",
                        servletContextPath);
                return;
            }
            String saveResult = codeService.saveGeneratedCode(servletContextPath.toString(), updated);
            if (saveResult == null || saveResult.startsWith("파일 저장 실패")) {
                failed.add("servlet-context.xml — " + saveResult);
                log.error("[master-detail-orchestrate] servlet-context.xml 저장 실패: {}", servletContextPath);
            } else {
                log.info("[master-detail-orchestrate] servlet-context.xml component-scan 보강 완료: {}",
                        servletContextPath);
            }
        } catch (IOException e) {
            failed.add("servlet-context.xml — component-scan 보강 실패: " + e.getMessage());
            log.warn("[master-detail-orchestrate] servlet-context.xml 읽기 실패: {}", e.getMessage());
        }
    }

    private String deriveDetailDomain(String tableName) {
        String base = tableName.replace("COMTN", "").replace("COMTS", "").replace("COMTC", "");
        String[] parts = base.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isBlank()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }
}
