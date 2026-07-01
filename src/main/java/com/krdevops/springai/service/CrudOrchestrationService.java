package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
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

        CrudViewType resolvedViewType = CrudViewType.from(viewType);
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
        CrudTemplateModel model =
                crudModelFactory.fromSchema(tableName, domain, packageName, egovVersion, rawColumns);

        // 3. viewType별 CrudLayerDefinition 기준으로 렌더링 + 저장
        List<String> succeeded = new ArrayList<>();
        List<String> failed    = new ArrayList<>();

        for (CrudLayerDefinition layer : CrudLayerDefinition.forViewType(resolvedViewType)) {
            String fileName = CrudLayerDefinition.resolveFileName(
                    layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath  = layer.resolveSubPath(pkgSub, model.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code       = crudTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
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
        }

        // 4. 코드 검증
        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
            log.warn("[orchestrate] 코드 검증 실패: {}", e.getMessage());
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
                succeeded, failed, validationSummary, historySummary);
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

        String indexPath = outputPath + "/src/main/webapp/index.jsp";
        String listUrl = model.urlPrefix() + "List.do";
        String indexJsp = """
<%%@ page contentType="text/html;charset=UTF-8" %%>
<jsp:forward page="%s"/>
""".formatted(listUrl);

        String saveResult = codeService.saveGeneratedCode(indexPath, indexJsp);
        if (saveResult == null || saveResult.startsWith("파일 저장 실패")) {
            failed.add("index.jsp — " + saveResult);
            log.error("[orchestrate] index.jsp 기본 진입점 갱신 실패: {}", indexPath);
        } else {
            log.info("[orchestrate] index.jsp 기본 진입점 갱신 완료: {} -> {}", indexPath, listUrl);
        }
    }
}
