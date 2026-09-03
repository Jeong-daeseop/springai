package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.krdevops.springai.service.generation.crud.CrudPipelineFixture;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

/**
 * 계획서 §10.6이 서술하는 CRUD Processor 실행 순서
 * (PRE_WRITE 100 Table Density CSS → 110 Form Column CSS → WRITE(레이어별 렌더링/저장)
 * → POST_WRITE 100 Entry Point → 200 Thymeleaf Runtime → 210 Controller Scan → 300 MyBatis
 * → PRE_VERIFY 100 Common Contract → VERIFY → HISTORY)와
 * {@link CrudOrchestrationService#orchestrate}의 실제 협력 객체 호출 순서를 대조해
 * Mockito {@link InOrder}로 캡처·문서화하는 Characterization 테스트다.
 *
 * <p>WP-4에서 {@code CrudOrchestrationService}가 Compatibility Facade가 되면서 SUT 내부는
 * 실제 Pipeline({@code CrudGenerationApplicationService})으로 바뀌었지만, 이 테스트가 검증하는
 * <b>협력 객체 호출 순서는 리팩터링 전과 동일해야 한다</b> — 그것이 이 테스트의 존재 이유다.
 *
 * <p><b>WP-0이 발견하고 WP-4가 해소한 순서 차이 2건</b>:
 * <ol>
 *   <li>명세서 §10.6/§11.1 표는 PRE_VERIFY(Common Contract 감사)가 VERIFY(Directory 검증)보다
 *       먼저라고 적었지만 실제 구현은 반대다. WP-4는 {@code CodeDirectoryVerifier}를
 *       {@code PRE_VERIFY}에, {@code CommonGeneratedContractVerifier}를 {@code VERIFY}에 배정해
 *       실제 순서를 보존했다.</li>
 *   <li>{@code GenerationStage} enum은 {@code RENDER}를 {@code PRE_WRITE}보다 앞에 두지만 실제
 *       구현은 CSS 보강(PRE_WRITE)을 템플릿 렌더링보다 먼저 수행한다. WP-4는 PRE_WRITE Processor를
 *       Renderer 앞에서 실행해 실제 순서를 보존했다.</li>
 * </ol>
 * 두 경우 모두 {@code ORT-PRN-005}(기존 동작 보존)가 문서의 표기보다 우선한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CrudOrchestrationProcessorOrderTest {

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

    CrudOrchestrationService sut;

    @BeforeEach
    void buildPipelineAndStubFullSuccessPath() {
        CrudPipelineFixture.createWarKrdsAssets(Path.of("/tmp/egov-test"));
        sut = new CrudOrchestrationService(CrudPipelineFixture.applicationService(
                crudSchemaQueryService, crudProgramMetadataService, generationDesignContextService,
                crudModelFactory, thymeleafLayoutValidator, routeCollisionDetector,
                crudTemplateRenderer, codeService, writePort, krdsStylesConfigurer, warEntryPointConfigurer,
                thymeleafRuntimeConfigurer, myBatisRuntimeConfigurer, codeValidatorService,
                generatedCodeContractAuditor, generationHistoryService));

        given(crudSchemaQueryService.fetchColumns(any(), any())).willReturn(fakeColumns());
        given(crudProgramMetadataService.resolve(any(), any(), any(), any()))
                .willReturn(CrudProgramMetadata.fallback("fallback"));
        given(routeCollisionDetector.findConflicts(any(), any(), any(), any())).willReturn(List.of());
        // 밀도/폼 컬럼 배치를 STANDARD/SINGLE_COLUMN이 아닌 값으로 고정해 PRE_WRITE 100/110이
        // 모두 실행되도록 한다.
        given(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(),
                any(CrudViewType.class), any(ScreenSubsetMode.class), any()))
                .willReturn(fakeModel(LayoutDensity.COMPACT, FormColumnLayout.TWO_COLUMN));
        given(krdsStylesConfigurer.ensureTableDensityStyles(any()))
                .willReturn(new KrdsStylesConfigurer.CssPatchResult(
                        KrdsStylesConfigurer.Status.PATCHED, "styles.css", "OK"));
        given(krdsStylesConfigurer.ensureFormColumnLayoutStyles(any()))
                .willReturn(new KrdsStylesConfigurer.CssPatchResult(
                        KrdsStylesConfigurer.Status.PATCHED, "styles.css", "OK"));
        given(crudTemplateRenderer.renderByLayerKey(any(), any(), any(), any(), any(), any()))
                .willReturn("// code");
        given(writePort.apply(any())).willAnswer(CrudPipelineFixture.alwaysSucceeds());
        given(warEntryPointConfigurer.configure(any(), any()))
                .willReturn(WarEntryPointConfigurer.ConfigurationResult.success("완료"));
        given(myBatisRuntimeConfigurer.ensureConfigured(any(), any()))
                .willReturn(MyBatisRuntimeConfigurer.ConfigurationResult.success(
                        Path.of("/tmp/context-common.xml"), false, "검증 통과"));
        given(codeValidatorService.validateDirectory(any())).willReturn("검증 통과");
        given(generatedCodeContractAuditor.audit(any())).willReturn(List.of());
        given(generationHistoryService.saveHistory(any(), any(), any(), any(), any()))
                .willReturn("이력 저장 완료");
    }

    @Test
    void orchestrate_thymeleafCreateWithNonStandardDensityAndFormLayout_callsCollaboratorsInDocumentedOrder() {
        sut.orchestrate("com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-test", "5.0", "thymeleaf", "create", null, null);

        InOrder order = inOrder(
                krdsStylesConfigurer, crudTemplateRenderer, writePort, warEntryPointConfigurer,
                thymeleafRuntimeConfigurer, myBatisRuntimeConfigurer, codeValidatorService,
                generatedCodeContractAuditor, generationHistoryService);

        // PRE_WRITE 100: Table Density CSS
        order.verify(krdsStylesConfigurer).ensureTableDensityStyles("/tmp/egov-test");
        // PRE_WRITE 110: Form Column CSS
        order.verify(krdsStylesConfigurer).ensureFormColumnLayoutStyles("/tmp/egov-test");
        // WRITE: 레이어별 렌더링 + 저장(모든 레이어를 한 ProjectChangeSet으로 묶어 1회 적용,
        // WP7 2차 pass — ApprovedProjectWritePort로 전환)
        order.verify(crudTemplateRenderer, Mockito.atLeastOnce())
                .renderByLayerKey(any(), any(), any(), any(), any(), any());
        order.verify(writePort).apply(any());
        // POST_WRITE 100: Entry Point(index.jsp 기본 진입점 갱신)
        order.verify(warEntryPointConfigurer).configure(any(), any());
        // POST_WRITE 200: Thymeleaf Runtime
        order.verify(thymeleafRuntimeConfigurer).ensureThymeleafRuntime(any(), any(), any());
        // POST_WRITE 210: Controller Scan
        order.verify(thymeleafRuntimeConfigurer).ensureControllerComponentScan(any(), any(), any());
        // POST_WRITE 300: MyBatis
        order.verify(myBatisRuntimeConfigurer).ensureConfigured(any(), any());
        // VERIFY — 클래스 Javadoc 참고: 명세서는 이 앞에 PRE_VERIFY 100을 두지만
        // 실제 구현은 코드 검증(VERIFY)을 먼저 실행한다.
        order.verify(codeValidatorService).validateDirectory(any());
        // 실제 구현상 Common Contract 감사는 VERIFY "다음"에 실행된다(명세서 PRE_VERIFY 순서와 다름).
        order.verify(generatedCodeContractAuditor).audit(any());
        // HISTORY
        order.verify(generationHistoryService).saveHistory(any(), any(), any(), any(), any());
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
                "EMPLYR_ID", "emplyrId", "String", "직원ID",
                true, true, true, 20, "VARCHAR");
        return new CrudTemplateModel(
                "egovframework.let.emp", "Employer", "employer", "직원",
                "LETTNEMPLYRINFO", "/emp/employer", "2026-07-31", "5.0", true,
                pk, List.of(pkField), List.of(pkField), List.of(pkField), List.of(), List.of(),
                fakeRoute(), null, List.of(pkField), density, formColumnLayout);
    }

    private static CrudRouteModel fakeRoute() {
        return new CrudRouteModel(
                "/emp/employerList.do", null,
                "/emp/employerDetail.do", null,
                "/emp/employerRegistView.do", null,
                "/emp/employerRegist.do", null,
                "/emp/employerUpdtView.do", null,
                "/emp/employerUpdt.do", null,
                "/emp/employerDelete.do", null,
                null);
    }
}
