package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MasterDetailOrchestrationServiceTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock MasterDetailTemplateRenderer masterDetailTemplateRenderer;
    @Mock CodeService codeService;
    @Mock CodeValidatorService codeValidatorService;
    @Mock GenerationHistoryService generationHistoryService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    @Spy ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @InjectMocks
    MasterDetailOrchestrationService service;

    @Test
    void thymeleafReuseWithoutLayout_returnsFailureBeforeSave(@TempDir Path tempDir) {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com",
                "COMTNBBSMASTER",
                "COMTNBBSUSE",
                "BbsMaster",
                "egovframework.let.bbs",
                tempDir.toString(),
                "5.0",
                "thymeleaf");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout\"");
        verify(masterDetailTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void thymeleafReuseCustomLayoutView_missingFileUnderCustomBase_returnsFailureBeforeSave(
            @TempDir Path tempDir) throws Exception {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout/admin\"")
                .contains("footer.html");
        verify(masterDetailTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void thymeleafReuseCustomLayoutView_allFilesPresent_rendersWithCustomPaths(
            @TempDir Path tempDir) throws Exception {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any()))
                .willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        // reuse 기본값(13레이어, layout 제외) + EgovMainController.java = 14
        assertThat(result.successCount()).isEqualTo(14);
        assertThat(result.failedFiles()).isEmpty();
        verify(masterDetailTemplateRenderer, atLeastOnce()).renderByLayerKey(
                any(), any(),
                eq("layout/admin/default"), eq("layout/admin/breadcrumb"), eq("layout/admin"), any());
    }

    // ─── JSP / CREATE 파일 수 ─────────────────────────────────────────────────

    @Test
    void jsp_succeededFiles_is14() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        // JSP 13레이어 + EgovMainController.java = 14
        assertThat(result.successCount()).isEqualTo(14);
        assertThat(result.failedFiles()).isEmpty();
    }

    @Test
    void thymeleafCreateMode_succeededFiles_is19() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        // THYMELEAF create 18레이어 + EgovMainController.java = 19
        assertThat(result.successCount()).isEqualTo(19);
        assertThat(result.failedFiles()).isEmpty();
    }

    // ─── 테이블 미존재 ────────────────────────────────────────────────────────

    @Test
    void tableNotFound_returnsNotFoundResult() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(List.of());

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.succeededFiles()).isEmpty();
    }

    // ─── ThymeleafRuntimeConfigurer 호출 ──────────────────────────────────────

    @Test
    void thymeleaf_ensureThymeleafRuntime_isCalled() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        verify(thymeleafRuntimeConfigurer).ensureThymeleafRuntime(eq("/tmp/md-test"), eq("5.0"), any());
    }

    @Test
    void jsp_ensureThymeleafRuntime_notCalled() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), any());
    }

    // ─── 저장/렌더/검증/이력 실패 ─────────────────────────────────────────────

    @Test
    void saveFails_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 실패: 권한 없음");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        // JSP 13레이어 + EgovMainController.java = 14
        assertThat(result.failCount()).isEqualTo(14);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("파일 저장 실패: 권한 없음"));
    }

    @Test
    void renderThrows_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any()))
                .willThrow(new RuntimeException("템플릿 로딩 실패"));
        // EgovMainController.java는 renderByLayerKey를 거치지 않고 별도 저장되므로 성공 처리해 분리 검증
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failedFiles())
                .hasSize(13)
                .allSatisfy(f -> assertThat(f).contains("템플릿 로딩 실패"));
        assertThat(result.succeededFiles()).containsExactly("EgovMainController.java");
    }

    @Test
    void validationThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any()))
                .willThrow(new RuntimeException("검증 서비스 오류"));
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(14);
        assertThat(result.validationSummary()).contains("검증 실패:");
    }

    @Test
    void historyThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(14);
        assertThat(result.historySummary()).contains("생성 이력 저장 실패:");
    }

    private static List<Map<String, Object>> fakeColumns(String pkColumn) {
        Map<String, Object> col = new HashMap<>();
        col.put("COLUMN_NAME", pkColumn);
        col.put("DATA_TYPE", "varchar");
        col.put("CHARACTER_MAXIMUM_LENGTH", 20L);
        col.put("IS_NULLABLE", "NO");
        col.put("COLUMN_COMMENT", pkColumn);
        col.put("COLUMN_KEY", "PRI");
        return List.of(col);
    }

    private static CrudTemplateModel fakeModel(String domain, String domainLc) {
        PkModel pk = new PkModel("BBS_ID", "bbsId", "String");
        FieldModel pkField = new FieldModel(
                "BBS_ID", "bbsId", "String", "게시판ID",
                true, true, true, 20, "VARCHAR");
        return new CrudTemplateModel(
                "egovframework.let.bbs", domain, domainLc, domain,
                "COMTNBBSMASTER", "/bbs/" + domainLc, "2026-07-02", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of());
    }
}
