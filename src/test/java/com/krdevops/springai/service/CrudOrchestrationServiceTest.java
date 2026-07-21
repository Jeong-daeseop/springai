package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.LayoutDensity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrudOrchestrationServiceTest {

    @Mock CrudSchemaQueryService   crudSchemaQueryService;
    @Mock CrudModelFactory         crudModelFactory;
    @Mock CrudTemplateRenderer     crudTemplateRenderer;
    @Mock CodeService              codeService;
    @Mock CodeValidatorService     codeValidatorService;
    @Mock GenerationHistoryService generationHistoryService;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock BoardRouteCollisionDetector routeCollisionDetector;
    @Mock MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    @Mock WarEntryPointConfigurer warEntryPointConfigurer;
    @Mock GenerationDesignContextService generationDesignContextService;
    @Mock GeneratedCodeContractAuditor generatedCodeContractAuditor;
    @Mock KrdsStylesConfigurer krdsStylesConfigurer;
    @Spy ThymeleafLayoutValidator thymeleafLayoutValidator = new ThymeleafLayoutValidator();

    @InjectMocks
    CrudOrchestrationService sut;

    @BeforeEach
    void stubMetadataInfrastructure() {
        lenient().when(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .thenReturn(CrudProgramMetadata.fallback("fallback"));
        lenient().when(routeCollisionDetector.findConflicts(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(myBatisRuntimeConfigurer.ensureConfigured(any(), any()))
                .thenReturn(MyBatisRuntimeConfigurer.ConfigurationResult.success(
                        Path.of("/tmp/context-common.xml"), false, "검증 통과"));
        lenient().when(warEntryPointConfigurer.configure(any(), any()))
                .thenReturn(WarEntryPointConfigurer.ConfigurationResult.success("완료"));
    }

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

        verify(crudModelFactory, never()).fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any());
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
    }

    // ── 프로그램 메타데이터(LETTNPROGRMLIST) 조회 ─────────────────────────────

    @Test
    void orchestrate_ambiguousListMetadata_stopsBeforeRenderingAndSaving() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(new CrudProgramMetadata(null, null, null, null, java.util.Map.of(), null,
                        CrudProgramMetadata.Source.DATABASE, CrudProgramMetadata.Status.AMBIGUOUS,
                        "list 화면 프로그램 메타데이터가 2건으로 중복되었습니다."));

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.failedFiles()).singleElement().asString().contains("중복");
        verify(crudModelFactory, never()).fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any());
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void orchestrate_resolvedMetadata_reportedInResult() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(new CrudProgramMetadata("EgovBoardMstrList", "/cop/bbs/", "게시판 목록조회", "게시판생성관리",
                        java.util.Map.of("list", "/cop/bbs/SelectBBSMasterInfs.do"),
                        "/cop/bbs/SelectBBSMasterInfs.do",
                        CrudProgramMetadata.Source.DATABASE, CrudProgramMetadata.Status.RESOLVED, null));
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.menuIntegrationStatus()).isEqualTo("DB URL + GNB/LNB 연동");
        assertThat(result.resolvedProgramName()).isEqualTo("게시판 목록조회");
    }

    // ── 화면명세(ScreenSpecification) 결합 ────────────────────────────────────
    // design-vision-tool-test-priority-detail.md §2 우선순위 2 후속 —
    // auto 경로가 REVIEW_REQUIRED/미승인 화면명세를 실제로 어떻게 처리하는지 확인한다.
    // metadata.programKoreanName()은 fallback() 기준 null이므로 그대로 전달되는지도 함께 검증한다.

    @Test
    void orchestrate_designSpecResolutionThrows_propagatesUncaughtWithoutPartialGeneration() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", null, "crud", "analysis-1", null))
                .willThrow(new IllegalStateException(
                        "APPROVED 화면명세만 코드 생성에 사용할 수 있습니다: spec-1 (REVIEW_REQUIRED)"));
        CrudGenerationOptions options = new CrudGenerationOptions(
                null, null, null, null, "analysis-1", null);

        assertThatThrownBy(() -> sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0", "jsp", null, null, null, options))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVIEW_REQUIRED");

        verify(crudModelFactory, never()).fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any());
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void orchestrate_approvedScreenSpecification_passedToExplicitModelFactoryOverload() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        ScreenSpecification approved = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        CrudGenerationOptions options = new CrudGenerationOptions(
                null, null, null, null, null, "spec-1");
        given(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", null, "crud", null, "spec-1"))
                .willReturn(approved);
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.JSP), eq(ScreenSubsetMode.LIST_ONLY), eq(approved)))
                .willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0", "jsp", null, null, null, options);

        assertThat(result.tableNotFound()).isFalse();
        verify(crudModelFactory).fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.JSP), eq(ScreenSubsetMode.LIST_ONLY), eq(approved));
        verify(crudModelFactory, never()).fromSchema(any(), any(), any(), any(), any(), any());
    }

    @Test
    void orchestrate_checksCollisionForEveryAlias_notOnlyList() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModelWithDetailAlias());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        // list는 alias가 없으므로 검사되지 않고, detail alias만 GET으로 검사되어야 한다.
        verify(routeCollisionDetector).findConflicts(
                eq("/tmp/egov-test"), eq("/emp/employerDetailRegistered.do"), eq("GET"), anyString());
        verify(routeCollisionDetector, never()).findConflicts(
                eq("/tmp/egov-test"), eq("/emp/employerList.do"), any(), anyString());
    }

    @Test
    void orchestrate_aliasConflictOnDetail_blocksGeneration() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModelWithDetailAlias());
        given(routeCollisionDetector.findConflicts(
                eq("/tmp/egov-test"), eq("/emp/employerDetailRegistered.do"), eq("GET"), anyString()))
                .willReturn(List.of("/tmp/egov-test/.../OtherController.java"));

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.failedFiles()).singleElement().asString().contains("충돌");
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    // ── 정상 생성 ────────────────────────────────────────────────────────────

    @Test
    void orchestrate_allLayersSaved_returns11SucceededFiles() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// generated code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: /tmp/...");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 통과");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willReturn("이력 저장 완료");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.tableNotFound()).isFalse();
        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.hasFailure()).isFalse();
        assertThat(result.validationSummary()).isEqualTo("검증 통과");
        assertThat(result.historySummary()).isEqualTo("이력 저장 완료");
        verify(myBatisRuntimeConfigurer).ensureConfigured(
                "/tmp/egov-test", "egovframework.let.emp.service.impl");
    }

    @Test
    void orchestrate_succeededFiles_containsExpectedFileNames() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(crudTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
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
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        doReturn("// code").when(crudTemplateRenderer).renderByLayerKey(any(), any(), any(), any(), any(), any());
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
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
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0", "thymeleaf",
                "create", null, null);

        // Thymeleaf는 layout partial 포함 16개
        assertThat(result.successCount()).isEqualTo(16);
        assertThat(result.succeededFiles())
                .contains("layout/default.html")
                .contains("layout/gnb.html")
                .contains("layout/lnb.html")
                .contains("layout/breadcrumb.html")
                .contains("layout/footer.html")
                .contains("EgovEmployerList.html")
                .contains("EgovEmployerDetail.html")
                .contains("EgovEmployerRegist.html")
                .contains("EgovEmployerUpdt.html")
                .doesNotContain("EgovEmployerList.jsp");
        verify(crudTemplateRenderer, atLeastOnce()).renderByLayerKey(
                eq("thymeleafList"), any(), any(), any(), any(), any());
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                eq("/tmp/egov-test/src/main/resources/templates/employer/EgovEmployerList.html"), any());
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                eq("/tmp/egov-test/src/main/resources/templates/layout/default.html"), any());
        verify(codeService, atLeastOnce()).saveGeneratedCode(
                eq("/tmp/egov-test/src/main/resources/templates/layout/gnb.html"), any());
        verify(crudModelFactory).fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.LIST_AND_DETAIL), isNull());
    }

    @Test
    void orchestrate_thymeleaf_ensuresControllerComponentScan() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(crudTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0", "thymeleaf",
                "create", null, null);

        verify(thymeleafRuntimeConfigurer).ensureControllerComponentScan(
                eq("/tmp/egov-test"), eq("egovframework.let.emp.web"), any());
    }

    @Test
    void orchestrate_jsp_doesNotEnsureControllerComponentScan() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        verify(thymeleafRuntimeConfigurer, never()).ensureControllerComponentScan(any(), any(), any());
    }

    @Test
    void orchestrate_thymeleafReuseWithoutLayout_returnsFailureBeforeSave(@TempDir Path tempDir) {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", tempDir.toString(), "5.0", "thymeleaf");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout\"");
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void orchestrate_thymeleafReuseCustomLayoutView_missingFileUnderCustomBase_returnsFailureBeforeSave(
            @TempDir Path tempDir) throws Exception {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        // footer.html 누락 — 나머지 4종만 생성
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        assertThat(result.successCount()).isZero();
        assertThat(result.failedFiles()).singleElement()
                .asString()
                .contains("generateThymeleafLayout")
                .contains("layoutBasePath=\"layout/admin\"")
                .contains("footer.html");
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any(), any(), any(), any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    @Test
    void orchestrate_thymeleafReuseCustomLayoutView_allFilesPresent_rendersWithCustomPaths(
            @TempDir Path tempDir) throws Exception {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        Path customBase = tempDir.resolve("src/main/resources/templates/layout/admin");
        java.nio.file.Files.createDirectories(customBase);
        for (String name : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html")) {
            java.nio.file.Files.writeString(customBase.resolve(name), "<html></html>");
        }

        CrudOrchestrationResult result = sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", tempDir.toString(), "5.0", "thymeleaf",
                "reuse", "layout/admin/default", "layout/admin/breadcrumb");

        // reuse 기본값은 layout 레이어를 저장하지 않으므로 화면/Java/Mapper 11개만 성공
        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.hasFailure()).isFalse();
        verify(crudTemplateRenderer, atLeastOnce()).renderByLayerKey(
                eq("thymeleafList"), any(),
                eq("layout/admin/default"), eq("layout/admin/breadcrumb"), eq("layout/admin"), any());
    }

    @Test
    void orchestrate_updatesIndexJspToGeneratedListUrl() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", "/tmp/egov-test", "5.0");

        verify(warEntryPointConfigurer).configure("/tmp/egov-test", "/emp/employerList.do");
    }

    // ── 저장 실패 ────────────────────────────────────────────────────────────

    @Test
    void orchestrate_saveFails_recordsInFailedFiles() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 실패: 권한 없음");
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
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
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any()))
                .willThrow(new RuntimeException("템플릿 로딩 실패"));
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 실패");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.hasFailure()).isTrue();
        assertThat(result.failedFiles())
                .allSatisfy(f -> assertThat(f).contains("템플릿 로딩 실패"));
    }

    // ── 검증/이력 실패 — 저장 성공에는 영향 없음 ────────────────────────────

    @Test
    void orchestrate_validationThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any()))
                .willThrow(new RuntimeException("검증 서비스 오류"));
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any())).willReturn("OK");

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.validationSummary()).contains("검증 실패:");
    }

    @Test
    void orchestrate_historyThrows_resultStillReturned() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(CrudViewType.class), any(ScreenSubsetMode.class), any())).willReturn(fakeModel());
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(codeService.saveGeneratedCode(any(), any())).willReturn("파일 저장 완료: ...");
        given(codeValidatorService.validateDirectory(any())).willReturn("OK");
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), anyString()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        CrudOrchestrationResult result =
                sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer",
                        "egovframework.let.emp", "/tmp/egov-test", "5.0");

        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.historySummary()).contains("생성 이력 저장 실패:");
    }

    @Test
    void densityCssFailureReturnsBeforeAnyGeneratedFileOrRuntimeMutation() {
        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        ScreenSpecification approved = new ScreenSpecification(
                "spec-density", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(),
                LayoutDensity.COMPACT, null);
        given(generationDesignContextService.resolve(any(), any(), any(), any(), any(), any()))
                .willReturn(approved);
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                eq(CrudViewType.THYMELEAF), eq(ScreenSubsetMode.LIST_AND_DETAIL), eq(approved)))
                .willReturn(fakeModel(LayoutDensity.COMPACT));
        given(krdsStylesConfigurer.ensureTableDensityStyles("/tmp/egov-test"))
                .willReturn(new KrdsStylesConfigurer.CssPatchResult(
                        KrdsStylesConfigurer.Status.FAILED, null, "marker 손상"));

        CrudOrchestrationResult result = sut.orchestrate(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-test", "5.0", "thymeleaf", "create", null, null,
                new CrudGenerationOptions(null, null, null, null, null, "spec-density"));

        assertThat(result.failedFiles()).containsExactly("styles.css — marker 손상");
        verify(codeService, never()).saveGeneratedCode(any(), any());
        verify(warEntryPointConfigurer, never()).configure(any(), any());
        verify(thymeleafRuntimeConfigurer, never()).ensureThymeleafRuntime(any(), any(), any());
        verify(thymeleafRuntimeConfigurer, never()).ensureControllerComponentScan(any(), any(), any());
        verify(myBatisRuntimeConfigurer, never()).ensureConfigured(any(), any());
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
                "LETTNEMPLYRINFO", "/emp/employer", "2026-06-17", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of());
    }

    private static CrudTemplateModel fakeModel(LayoutDensity density) {
        CrudTemplateModel base = fakeModel();
        return new CrudTemplateModel(
                base.packageName(), base.domain(), base.domainLc(), base.domainKr(), base.tableName(),
                base.urlPrefix(), base.date(), base.egovVersion(), base.jakartaValidation(), base.pk(),
                base.pkFields(), base.fields(), base.listFields(), base.nonPkFields(), base.formFields(),
                base.route(), base.queryContract(), base.fields(), density);
    }

    /** detail role에만 DB alias가 걸린 모델 — 7개 alias를 개별 검사하는지 확인하는 테스트용. */
    private static CrudTemplateModel fakeModelWithDetailAlias() {
        PkModel pk = new PkModel("EMPLYR_ID", "emplyrId", "String");
        FieldModel pkField = new FieldModel(
                "EMPLYR_ID", "emplyrId", "String", "직원ID",
                true, true, true, 20, "VARCHAR");
        CrudRouteModel route = new CrudRouteModel(
                "/emp/employerList.do", null,
                "/emp/employerDetail.do", "/emp/employerDetailRegistered.do",
                "/emp/employerRegistView.do", null,
                "/emp/employerRegist.do", null,
                "/emp/employerUpdtView.do", null,
                "/emp/employerUpdt.do", null,
                "/emp/employerDelete.do", null,
                null);
        return new CrudTemplateModel(
                "egovframework.let.emp", "Employer", "employer", "직원",
                "LETTNEMPLYRINFO", "/emp/employer", "2026-06-17", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of(), route);
    }
}
