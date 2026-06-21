package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CrudOrchestrationServiceTest {

    @Mock CrudSchemaQueryService   crudSchemaQueryService;
    @Mock CrudModelFactory         crudModelFactory;
    @Mock CrudTemplateRenderer     crudTemplateRenderer;
    @Mock CodeService              codeService;
    @Mock CodeValidatorService     codeValidatorService;
    @Mock GenerationHistoryService generationHistoryService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

    @InjectMocks
    CrudOrchestrationService sut;

    // ── 테이블 미존재 ────────────────────────────────────────────────────────

    @Test
    void orchestrate_tableNotFound_returnsNotFoundResult() {
        given(crudSchemaQueryService.fetchColumns("com", "NOTEXIST")).willReturn(List.of());

        CrudOrchestrationResult result =
                sut.orchestrate("com", "NOTEXIST", "Test", "egovframework.let.test", "/tmp", "5.0");

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.database()).isEqualTo("com");
        assertThat(result.tableName()).isEqualTo("NOTEXIST");
        assertThat(result.succeededFiles()).isEmpty();
        assertThat(result.failedFiles()).isEmpty();

        verify(crudModelFactory, never()).fromSchema(any(), any(), any(), any(), any());
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
    }

    // ── 정상 생성 ────────────────────────────────────────────────────────────

    @Test
    void orchestrate_allLayersSaved_returns11SucceededFiles() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// generated code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: /tmp/...");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 통과");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willReturn("이력 저장 완료");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.tableNotFound()).isFalse();
        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.hasFailure()).isFalse();
        assertThat(result.validationSummary()).isEqualTo("검증 통과");
        assertThat(result.historySummary()).isEqualTo("이력 저장 완료");
    }

    @Test
    void orchestrate_succeededFiles_containsExpectedFileNames() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.succeededFiles())
                .contains("EmployerVO.java")
                .contains("EmployerMapper.java")
                .contains("EmployerMapper.xml")
                .contains("EmployerService.java")
                .contains("EgovEmployerServiceImpl.java")
                .contains("EgovEmployerController.java")
                .contains("EgovEmployerValidationHandler.java")
                .contains("EgovEmployerList.jsp")
                .contains("EgovEmployerDetail.jsp")
                .contains("EgovEmployerRegist.jsp")
                .contains("EgovEmployerUpdt.jsp");
    }

    @Test
    void orchestrate_savePaths_followProjectInitializrWarLayout() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        verify(codeService, atLeastOnce()).saveGeneratedCode(
                "/tmp/egov-test/src/main/java/egovframework/let/emp/service/EmployerVO.java", "// code");
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                "/tmp/egov-test/src/main/resources/egovframework/mapper/employer/EmployerMapper.xml", "// code");
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                "/tmp/egov-test/src/main/webapp/WEB-INF/jsp/employer/EgovEmployerList.jsp", "// code");
    }

    @Test
    void orchestrate_thymeleafViewType_savesHtmlUnderResourcesTemplates() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result = sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0", "thymeleaf");

        // Thymeleaf는 layout/default.html 포함 12개
        assertThat(result.successCount()).isEqualTo(12);
        assertThat(result.succeededFiles())
                .contains("layout/default.html")
                .contains("EgovEmployerList.html")
                .contains("EgovEmployerDetail.html")
                .contains("EgovEmployerRegist.html")
                .contains("EgovEmployerUpdt.html")
                .doesNotContain("EgovEmployerList.jsp");
        verify(crudTemplateRenderer, atLeastOnce()).renderByLayerKey("thymeleafList", fakeModel());
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                "/tmp/egov-test/src/main/resources/templates/employer/EgovEmployerList.html", "// code");
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                "/tmp/egov-test/src/main/resources/templates/layout/default.html", "// code");
    }

    @Test
    void orchestrate_updatesIndexJspToGeneratedListUrl() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        verify(codeService).saveGeneratedCode(
                "/tmp/egov-test/src/main/webapp/index.jsp",
                """
<%@ page contentType="text/html;charset=UTF-8" %>
<jsp:forward page="/emp/employerList.do"/>
""");
    }

    // ── 저장 실패 ────────────────────────────────────────────────────────────

    @Test
    void orchestrate_saveFails_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 실패: 권한 없음");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failCount()).isEqualTo(11);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("파일 저장 실패: 권한 없음"));
    }

    @Test
    void orchestrate_renderThrows_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any()))
                .willThrow(new RuntimeException("템플릿 로딩 실패"));
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("템플릿 로딩 실패"));
    }

    // ── 검증/이력 실패 — 저장 성공에는 영향 없음 ────────────────────────────

    @Test
    void orchestrate_validationThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any()))
                .willThrow(new RuntimeException("검증 서비스 오류"));
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.validationSummary()).contains("검증 실패:");
    }

    @Test
    void orchestrate_historyThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), anyString()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        CrudOrchestrationResult result =
                sut.orchestrate("com", "COMTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.historySummary()).contains("생성 이력 저장 실패:");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static List<Map<String, Object>> fakeColumns() {
        Map<String, Object> col = new HashMap<>();
        col.put("COLUMN_NAME", "EMPLYR_ID");
        col.put("DATA_TYPE", "varchar");
        col.put("CHARACTER_MAXIMUM_LENGTH", 20L);
        col.put("IS_NULLABLE", "NO");
        col.put("COLUMN_COMMENT", "직원ID");
        col.put("COLUMN_KEY", "PRI");
        return List.of(col);
    }

    private static CrudTemplateModel fakeModel() {
        PkModel pk = new PkModel("EMPLYR_ID", "emplyrId", "String");
        FieldModel pkField = new FieldModel(
                "EMPLYR_ID", "emplyrId", "String", "직원ID",
                true, true, true, 20, "VARCHAR");
        return new CrudTemplateModel(
                "egovframework.let.emp", "Employer", "employer", "직원",
                "COMTNEMPLYRINFO", "/emp/employer", "2026-06-17", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of());
    }
}
