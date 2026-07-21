package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
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
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    @Mock GeneratedCodeContractAuditor generatedCodeContractAuditor;
    @Spy ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @InjectMocks
    MasterDetailOrchestrationService service;

    @org.junit.jupiter.api.BeforeEach
    void stubMyBatisRuntime() {
        org.mockito.Mockito.lenient().when(myBatisRuntimeConfigurer.ensureConfigured(any(), any()))
                .thenReturn(MyBatisRuntimeConfigurer.ConfigurationResult.success(
                        Path.of("/tmp/context-common.xml"), false, "검증 통과"));
    }

    @Test
    void thymeleafReuseWithoutLayout_returnsFailureBeforeSave(@TempDir Path tempDir) {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com",
                "LETTNBBSMASTER",
                "LETTNBBSUSE",
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
    void screenSpecificationUsesActualViewTypeButDisablesSubsetForMasterDetail(@TempDir Path tempDir) {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "마스터", "master-detail", "CRUD_LIST",
                "com", "LETTNBBSMASTER", List.of(), List.of(), List.of(), null);
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.NONE), eq(specification)))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"));
        given(crudModelFactory.fromSchema(eq("LETTNBBSUSE"), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.NONE), isNull()))
                .willReturn(fakeModel("BbsUse", "bbsUse"));

        service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", null, null, specification);

        verify(crudModelFactory).fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.NONE), eq(specification));
        verify(crudModelFactory).fromSchema(eq("LETTNBBSUSE"), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.NONE), isNull());
    }

    @Test
    void thymeleafReuseCustomLayoutView_missingFileUnderCustomBase_returnsFailureBeforeSave(
            @TempDir Path tempDir) throws Exception {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
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
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
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
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        // reuse 기본값(14레이어, layout 제외) + EgovMainController.java = 15
        assertThat(result.successCount()).isEqualTo(15);
        assertThat(result.failedFiles()).isEmpty();
        verify(myBatisRuntimeConfigurer).ensureConfigured(
                tempDir.toString(), "egovframework.let.bbs.service.impl");
        verify(masterDetailTemplateRenderer, atLeastOnce()).renderByLayerKey(
                any(), any(),
                eq("layout/admin/default"), eq("layout/admin/breadcrumb"), eq("layout/admin"), any());
    }

    // ─── JSP / CREATE 파일 수 ─────────────────────────────────────────────────

    @Test
    void jsp_succeededFiles_is15() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        // JSP 14레이어 + EgovMainController.java = 15
        assertThat(result.successCount()).isEqualTo(15);
        assertThat(result.failedFiles()).isEmpty();
        verify(crudModelFactory, org.mockito.Mockito.times(2)).fromSchema(
                any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.JSP), eq(ScreenSubsetMode.NONE), isNull());
    }

    @Test
    void thymeleafCreateMode_succeededFiles_is20() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        // THYMELEAF create 19레이어 + EgovMainController.java = 20
        assertThat(result.successCount()).isEqualTo(20);
        assertThat(result.failedFiles()).isEmpty();
    }

    // ─── 테이블 미존재 ────────────────────────────────────────────────────────

    @Test
    void tableNotFound_returnsNotFoundResult() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(List.of());

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.succeededFiles()).isEmpty();
    }

    // ─── ThymeleafRuntimeConfigurer 호출 ──────────────────────────────────────

    @Test
    void thymeleaf_ensureThymeleafRuntime_isCalled() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "thymeleaf",
                "create", null, null);

        verify(thymeleafRuntimeConfigurer).ensureThymeleafRuntime(eq("/tmp/md-test"), eq("5.0"), any());
    }

    @Test
    void jsp_ensureThymeleafRuntime_notCalled() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), any());
    }

    // ─── 저장/렌더/검증/이력 실패 ─────────────────────────────────────────────

    @Test
    void saveFails_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 실패: 권한 없음");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        // JSP 14레이어 + EgovMainController.java = 15
        assertThat(result.failCount()).isEqualTo(15);
        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("파일 저장 실패: 권한 없음"));
    }

    @Test
    void renderThrows_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any()))
                .willThrow(new RuntimeException("템플릿 로딩 실패"));
        // EgovMainController.java는 renderByLayerKey를 거치지 않고 별도 저장되므로 성공 처리해 분리 검증
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failedFiles())
                .hasSize(14)
                .allSatisfy(f -> assertThat(f).contains("템플릿 로딩 실패"));
        assertThat(result.succeededFiles()).containsExactly("EgovMainController.java");
    }

    @Test
    void validationThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any()))
                .willThrow(new RuntimeException("검증 서비스 오류"));
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(15);
        assertThat(result.validationSummary()).contains("검증 실패:");
    }

    @Test
    void historyThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER")).willReturn(fakeColumns("BBS_ID"));
        given(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE")).willReturn(fakeColumns("BBS_ID"));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel("BbsMaster", "bbsMaster"), fakeModel("BbsUse", "bbsUse"));
        given(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        MasterDetailOrchestrationResult result = service.orchestrate(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/md-test", "5.0", "jsp");

        assertThat(result.successCount()).isEqualTo(15);
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
                "LETTNBBSMASTER", "/bbs/" + domainLc, "2026-07-02", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of());
    }
}
