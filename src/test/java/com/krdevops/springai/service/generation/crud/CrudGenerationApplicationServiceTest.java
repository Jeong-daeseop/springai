package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CodeValidatorService;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.GeneratedCodeContractAuditor;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.GenerationHistoryService;
import com.krdevops.springai.service.KrdsStylesConfigurer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.WarEntryPointConfigurer;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pipeline 자체의 단계 배치를 검증한다 — {@code CrudOrchestrationServiceTest}가 Facade를 통해
 * 관찰 가능한 결과(파일 수·메시지)를 지키는 것과 달리, 이 테스트는 "어느 단계가 먼저 실행되는가"를
 * 직접 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrudGenerationApplicationServiceTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock CrudTemplateRenderer crudTemplateRenderer;
    @Mock CodeService codeService;
    @Mock com.krdevops.springai.service.write.ApprovedProjectWritePort writePort;
    @Mock CodeValidatorService codeValidatorService;
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

    CrudGenerationApplicationService sut;

    @BeforeEach
    void buildPipelineAndStubSuccessPath() {
        sut = CrudPipelineFixture.applicationService(
                crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                crudTemplateRenderer, codeService, writePort, krdsStylesConfigurer, warEntryPointConfigurer,
                thymeleafRuntimeConfigurer, myBatisRuntimeConfigurer, codeValidatorService,
                generatedCodeContractAuditor, generationHistoryService);

        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(CrudProgramMetadata.fallback("fallback"));
        given(routeCollisionDetector.findConflicts(any(), any(), any(), any())).willReturn(List.of());
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel(LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN));
        given(crudTemplateRenderer.renderByLayerKey(any(), any())).willReturn("// code");
        given(writePort.apply(any())).willAnswer(CrudPipelineFixture.alwaysSucceeds());
        given(myBatisRuntimeConfigurer.ensureConfigured(any(), any()))
                .willReturn(new MyBatisRuntimeConfigurer.ConfigurationResult(
                        true, false, false, Path.of("/tmp/context-common.xml"), "검증 통과"));
        given(warEntryPointConfigurer.configure(any(), any()))
                .willReturn(WarEntryPointConfigurer.ConfigurationResult.success("완료"));
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 통과");
        given(generatedCodeContractAuditor.audit(any())).willReturn(List.of());
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willReturn("이력 저장 완료");
    }

    /**
     * {@code GenerationStage} enum은 RENDER를 PRE_WRITE보다 앞에 두지만, WP-0 실측 순서는
     * CSS 보강이 렌더링보다 먼저다 — Pipeline은 enum 배치가 아니라 실측 순서를 따라야 한다.
     */
    @Test
    void preWriteCssProcessorsRunBeforeTemplateRendering() {
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel(LayoutDensity.COMPACT, FormColumnLayout.TWO_COLUMN));
        given(krdsStylesConfigurer.ensureTableDensityStyles(any())).willReturn(cssOk());
        given(krdsStylesConfigurer.ensureFormColumnLayoutStyles(any())).willReturn(cssOk());

        sut.execute(command("jsp"));

        InOrder order = inOrder(krdsStylesConfigurer, crudTemplateRenderer);
        order.verify(krdsStylesConfigurer).ensureTableDensityStyles("/tmp/egov-test");
        order.verify(krdsStylesConfigurer).ensureFormColumnLayoutStyles("/tmp/egov-test");
        order.verify(crudTemplateRenderer, atLeastOnce()).renderByLayerKey(any(), any());
    }

    /** STOP 정책 — CSS 보강이 실패하면 렌더링조차 시도하지 않는다. */
    @Test
    void preWriteStopSkipsRenderingEntirely() {
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel(LayoutDensity.COMPACT, FormColumnLayout.SINGLE_COLUMN));
        given(krdsStylesConfigurer.ensureTableDensityStyles(any()))
                .willReturn(new KrdsStylesConfigurer.CssPatchResult(
                        KrdsStylesConfigurer.Status.FAILED, null, "marker 손상"));

        CrudOrchestrationResult result = sut.execute(command("jsp"));

        assertThat(result.validationSummary()).isEqualTo("CSS 보강 실패");
        assertThat(result.failedFiles()).containsExactly("styles.css — marker 손상");
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
        verify(codeService, never()).saveGeneratedCode(any(), any());
    }

    /**
     * Verifier는 Bean 주입 순서가 아니라 stage(PRE_VERIFY → VERIFY)로 정렬된다 —
     * {@link CrudPipelineFixture}는 일부러 반대 순서로 주입한다.
     */
    @Test
    void directoryValidationRunsBeforeContractAuditRegardlessOfInjectionOrder() {
        sut.execute(command("jsp"));

        InOrder order = inOrder(codeValidatorService, generatedCodeContractAuditor);
        order.verify(codeValidatorService).validateDirectory("/tmp/egov-test");
        order.verify(generatedCodeContractAuditor).audit("/tmp/egov-test");
    }

    @Test
    void contractAuditFailuresAppendToValidationSummaryAndFailedFiles() {
        given(generatedCodeContractAuditor.audit(any())).willReturn(List.of("계약 위반 1"));

        CrudOrchestrationResult result = sut.execute(command("jsp"));

        assertThat(result.validationSummary()).isEqualTo("검증 통과\n\n[생성 계약 감사]\n계약 위반 1");
        assertThat(result.failedFiles()).containsExactly("생성 계약 감사 — 계약 위반 1");
        assertThat(result.successCount()).isEqualTo(11);
    }

    /** 부분 실패 — 렌더링 실패와 저장 실패가 섞여도 레이어 순서대로 누적된다. */
    @Test
    void renderAndSaveFailuresAccumulateInLayerOrderWhileOthersSucceed() {
        given(crudTemplateRenderer.renderByLayerKey(contains("mapperXml"), any()))
                .willThrow(new RuntimeException("템플릿 로딩 실패"));
        doAnswer(CrudPipelineFixture.failingPaths(path -> path.contains("EmployerVO.java"), "권한 없음"))
                .when(writePort).apply(any());

        CrudOrchestrationResult result = sut.execute(command("jsp"));

        assertThat(result.failedFiles()).containsExactly(
                "EmployerVO.java — 파일 저장 실패: 권한 없음",
                "EmployerMapper.xml — 오류: 템플릿 로딩 실패");
        assertThat(result.successCount()).isEqualTo(9);
    }

    /** 이력 저장 실패는 비치명 — 이미 성공한 파일 생성을 취소하지 않는다. */
    @Test
    void historyFailureDoesNotCancelSucceededFiles() {
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("DB 연결 오류"));

        CrudOrchestrationResult result = sut.execute(command("jsp"));

        assertThat(result.successCount()).isEqualTo(11);
        assertThat(result.historySummary()).isEqualTo("생성 이력 저장 실패: DB 연결 오류");
        assertThat(result.failedFiles()).isEmpty();
    }

    @Test
    void historyRecordsSucceededFileCountOnly() {
        doAnswer(CrudPipelineFixture.failingPaths(path -> path.contains("EmployerVO.java"), "권한 없음"))
                .when(writePort).apply(any());

        sut.execute(command("jsp"));

        verify(generationHistoryService).saveHistory(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/egov-test", "10개 파일");
    }

    private static CrudGenerationCommand command(String viewType) {
        return new CrudGenerationCommand(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                Path.of("/tmp/egov-test"), "auto", "5.0", viewType,
                LayoutOptions.empty(), ProgramMetadataOverrides.empty(), DesignContextReference.empty());
    }

    private static KrdsStylesConfigurer.CssPatchResult cssOk() {
        return new KrdsStylesConfigurer.CssPatchResult(
                KrdsStylesConfigurer.Status.PATCHED, "styles.css", "OK");
    }

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

    private static CrudTemplateModel fakeModel(LayoutDensity density, FormColumnLayout formColumnLayout) {
        PkModel pk = new PkModel("EMPLYR_ID", "emplyrId", "String");
        FieldModel pkField = new FieldModel(
                "EMPLYR_ID", "emplyrId", "String", "직원ID", true, true, true, 20, "VARCHAR");
        CrudRouteModel route = new CrudRouteModel(
                "/emp/employerList.do", null,
                "/emp/employerDetail.do", null,
                "/emp/employerRegistView.do", null,
                "/emp/employerRegist.do", null,
                "/emp/employerUpdtView.do", null,
                "/emp/employerUpdt.do", null,
                "/emp/employerDelete.do", null,
                null);
        return new CrudTemplateModel(
                "egovframework.let.emp", "Employer", "employer", "직원",
                "LETTNEMPLYRINFO", "/emp/employer", "2026-07-31", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of(),
                route, null, List.of(pkField), density, formColumnLayout);
    }
}
