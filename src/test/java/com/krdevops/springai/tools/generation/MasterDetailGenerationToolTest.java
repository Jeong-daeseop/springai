package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.generation.api.BuildMasterDetailPromptUseCase;
import com.krdevops.springai.service.generation.api.DispatchMasterDetailGenerationUseCase;
import com.krdevops.springai.service.generation.api.GenerateMasterDetailProjectUseCase;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationDispatchService;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationPipelineService;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationResultAssembler;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailPipelineResult;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailProjectGenerationService;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailPromptGenerationService;
import com.krdevops.springai.service.generation.mcp.MasterDetailGenerationMcpFacade;
import com.krdevops.springai.service.generation.mcp.MasterDetailGenerationResultFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 마스터-디테일 MCP 진입점. {@link MasterDetailGenerationMcpFacade} →
 * {@link MasterDetailGenerationDispatchService}까지 실제 객체로 연결하고 최하위 협력자만 Mock 처리해
 * llmProvider(auto/claude) 분기 배선을 검증한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class MasterDetailGenerationToolTest {

    @Mock MasterDetailService masterDetailService;
    @Mock MasterDetailGenerationPipelineService masterDetailGenerationPipelineService;
    @Mock MasterDetailGenerationResultAssembler masterDetailGenerationResultAssembler;
    @Mock GenerationDesignContextService generationDesignContextService;

    MasterDetailGenerationTool tool;

    @BeforeEach
    void setUp() {
        GenerateMasterDetailProjectUseCase generateMasterDetailProjectUseCase = new MasterDetailProjectGenerationService(
                masterDetailGenerationPipelineService, masterDetailGenerationResultAssembler);
        BuildMasterDetailPromptUseCase buildMasterDetailPromptUseCase =
                new MasterDetailPromptGenerationService(generationDesignContextService, masterDetailService);
        DispatchMasterDetailGenerationUseCase dispatchMasterDetailGenerationUseCase =
                new MasterDetailGenerationDispatchService(
                        generateMasterDetailProjectUseCase, buildMasterDetailPromptUseCase);
        MasterDetailGenerationMcpFacade facade = new MasterDetailGenerationMcpFacade(
                dispatchMasterDetailGenerationUseCase, new MasterDetailGenerationResultFormatter());
        tool = new MasterDetailGenerationTool(facade);
    }

    @Test
    void buildMasterDetailPrompt_auto_passesResolvedScreenSpecificationToOrchestrator() {
        var pipelineResult = mock(MasterDetailPipelineResult.class);
        when(masterDetailGenerationPipelineService.execute(any())).thenReturn(pipelineResult);
        when(masterDetailGenerationResultAssembler.assemble(any(), eq(pipelineResult)))
                .thenReturn(new MasterDetailOrchestrationResult(false, "com", "LETTNEMPLYRINFO",
                        "LETTNEMPLYRATTRBINFO", "Employer", "/tmp/out",
                        List.of("EgovEmployerDetail.html"), List.of(), "OK", "OK"));

        String result = tool.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", null, null, "auto", null, null, null,
                "analysis-1", "spec-1");

        assertThat(result).contains("=== [auto] eGovFrame 마스터-디테일 CRUD 소스 생성 완료 ===");
        verify(masterDetailGenerationPipelineService).execute(any());
        verify(masterDetailService, never()).buildMasterDetailPrompt(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(ScreenSpecification.class));
    }

    @Test
    void buildMasterDetailPrompt_claude_passesResolvedScreenSpecificationToPromptBuilder() {
        ScreenSpecification screenSpecification = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "직원목록", "master-detail", "MASTER_DETAIL_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", "Employer", "master-detail", "analysis-1", "spec-1"))
                .thenReturn(screenSpecification);
        when(masterDetailService.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "jsp", null, null, null, screenSpecification))
                .thenReturn("MASTER_DETAIL_CLAUDE_PROMPT");

        String result = tool.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", null, null, "claude", null, null, null,
                "analysis-1", "spec-1");

        assertThat(result).isEqualTo("MASTER_DETAIL_CLAUDE_PROMPT");
        verify(masterDetailGenerationPipelineService, never()).execute(any());
    }

    @Test
    void buildMasterDetailPrompt_auto_passesLayoutOptionsThrough() {
        var pipelineResult = mock(MasterDetailPipelineResult.class);
        when(masterDetailGenerationPipelineService.execute(any())).thenReturn(pipelineResult);
        when(masterDetailGenerationResultAssembler.assemble(any(), eq(pipelineResult)))
                .thenReturn(new MasterDetailOrchestrationResult(false, "com", "LETTNEMPLYRINFO",
                        "LETTNEMPLYRATTRBINFO", "Employer", "/tmp/out",
                        List.of("EgovEmployerDetail.html"), List.of(), "OK", "OK"));

        String result = tool.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "thymeleaf", "5.0", "auto",
                "create", "layout/admin", "layout/admin-breadcrumb", null, null);

        assertThat(result).contains("=== [auto] eGovFrame 마스터-디테일 CRUD 소스 생성 완료 ===");
        verify(masterDetailGenerationPipelineService).execute(any());
    }

    @Test
    void buildMasterDetailPrompt_claude_passesLayoutOptionsThrough() {
        ScreenSpecification screenSpecification = new ScreenSpecification(
                "spec-4", 1, ScreenSpecStatus.APPROVED, "직원목록", "master-detail", "MASTER_DETAIL_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", "Employer", "master-detail", null, null))
                .thenReturn(screenSpecification);
        when(masterDetailService.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "thymeleaf",
                "create", "layout/admin", "layout/admin-breadcrumb", screenSpecification))
                .thenReturn("MASTER_DETAIL_LAYOUT_PROMPT");

        String result = tool.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "thymeleaf", null, "claude",
                "create", "layout/admin", "layout/admin-breadcrumb", null, null);

        assertThat(result).isEqualTo("MASTER_DETAIL_LAYOUT_PROMPT");
        verify(masterDetailService).buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "thymeleaf",
                "create", "layout/admin", "layout/admin-breadcrumb", screenSpecification);
    }
}
