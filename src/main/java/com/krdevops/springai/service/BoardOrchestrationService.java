package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
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

    public BoardOrchestrationResult orchestrate(
            String database,
            String domain, String packageName, String outputPath,
            String mainTable, String masterTable, String useTable,
            String fileTable, String fileDetailTable,
            String egovVersion, String viewType) {

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

        // 2. 모델 생성
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        BoardTemplateModel model = boardModelFactory.fromSchemas(
            mainTable, masterTable, useTable, fileDetailTable,
            domain, packageName, egovVersion, schemas);

        // 3. 레이어 렌더링 + 저장
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (BoardLayerDefinition layer : BoardLayerDefinition.forViewType(resolvedViewType)) {
            String fileName = BoardLayerDefinition.resolveFileName(
                layer.layerKey(), domain, layer.fileNameSuffix());
            String subPath = layer.resolveSubPath(pkgSub, model.domainLc());
            String filePath = outputPath + "/" + subPath + fileName;

            try {
                String code = boardTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
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
        if (resolvedViewType == CrudViewType.THYMELEAF) {
            thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath, egovVersion, failed);
        }
        updateDefaultIndexForward(outputPath, model, resolvedViewType, succeeded, failed);

        // 4. 검증
        String validationSummary;
        try {
            validationSummary = codeValidatorService.validateDirectory(outputPath);
        } catch (Exception e) {
            validationSummary = "검증 실패: " + e.getMessage();
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
            succeeded, failed, validationSummary, historySummary);
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

        String indexPath = outputPath + "/src/main/webapp/index.jsp";
        String listUrl = model.urlPrefix() + "List.do";
        String indexJsp = """
<%%@ page contentType="text/html;charset=UTF-8" %%>
<jsp:forward page="%s"/>
""".formatted(listUrl);

        String saveResult = codeService.saveGeneratedCode(indexPath, indexJsp);
        if (saveResult == null || saveResult.startsWith("파일 저장 실패")) {
            failed.add("index.jsp — " + saveResult);
            log.error("[board-orchestrate] index.jsp 기본 진입점 갱신 실패: {}", indexPath);
        } else {
            log.info("[board-orchestrate] index.jsp 기본 진입점 갱신 완료: {} -> {}", indexPath, listUrl);
        }
    }
}
