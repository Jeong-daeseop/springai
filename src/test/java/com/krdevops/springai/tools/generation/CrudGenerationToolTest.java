package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.generation.api.BuildCrudPromptUseCase;
import com.krdevops.springai.service.generation.api.DispatchCrudGenerationUseCase;
import com.krdevops.springai.service.generation.api.GenerateCrudProjectUseCase;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.crud.CrudGenerationDispatchService;
import com.krdevops.springai.service.generation.crud.CrudPromptGenerationService;
import com.krdevops.springai.service.generation.mcp.CrudGenerationMcpFacade;
import com.krdevops.springai.service.generation.mcp.CrudGenerationResultFormatter;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 CRUD MCP 진입점. {@link CrudGenerationMcpFacade} → {@link CrudGenerationDispatchService}까지
 * 실제 객체로 연결하고 최하위 협력자만 Mock 처리해 llmProvider(auto/claude) 분기 배선을 검증한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class CrudGenerationToolTest {

    @Mock GenerateCrudProjectUseCase generateCrudProjectUseCase;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock CrudPromptBuilderService crudPromptBuilderService;
    @Mock GenerationDesignContextService generationDesignContextService;

    CrudGenerationTool tool;

    @BeforeEach
    void setUp() {
        BuildCrudPromptUseCase buildCrudPromptUseCase = new CrudPromptGenerationService(
                crudProgramMetadataService, generationDesignContextService, crudPromptBuilderService);
        DispatchCrudGenerationUseCase dispatchCrudGenerationUseCase =
                new CrudGenerationDispatchService(generateCrudProjectUseCase, buildCrudPromptUseCase);
        CrudGenerationMcpFacade facade =
                new CrudGenerationMcpFacade(dispatchCrudGenerationUseCase, new CrudGenerationResultFormatter());
        tool = new CrudGenerationTool(facade);
    }

    // ── designReferenceId / screenSpecificationId 배선 회귀 테스트 ──────────────
    // local-vision-design-reference-integration-review.md §5가 지적한 auto/claude
    // 중복 분기 지점 — 두 provider 모두에서 실제로 배선되는지 확인한다.

    @Test
    void buildFullCrudPrompt_auto_passesDesignReferenceIdsThroughGenerationOptions() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                null, null, null, null, "analysis-1", "spec-1");
        CrudGenerationCommand expectedCommand = autoCommand(
                "5.0", "jsp", LayoutOptions.empty(), ProgramMetadataOverrides.empty(),
                new DesignContextReference("analysis-1", "spec-1"));
        when(generateCrudProjectUseCase.execute(expectedCommand))
                .thenReturn(new CrudOrchestrationResult(false, "com", "LETTNEMPLYRINFO", "Employer",
                        "/tmp/out", List.of("EgovEmployerList.html"), List.of(), "OK", "OK"));

        String result = tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "auto",
                null, null, null, null, null,
                null, null, null, null, "analysis-1", "spec-1");

        assertThat(result).contains("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===");
        verify(generateCrudProjectUseCase).execute(expectedCommand);
        assertThat(expectedCommand.toGenerationOptions()).isEqualTo(expectedOptions);
        verify(generationDesignContextService, never()).resolve(any(), any(), any(), any(), any(), any());
        verify(crudProgramMetadataService, never()).resolve(any(), any(), any(), any());
    }

    @Test
    void buildFullCrudPrompt_claude_resolvesScreenSpecificationAndPassesToPromptBuilder() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                null, null, null, null, "analysis-1", "spec-1");
        CrudProgramMetadata metadata = CrudProgramMetadata.fallback("fallback");
        ScreenSpecification screenSpecification = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        when(crudProgramMetadataService.resolve("com", "Employer", "LETTNEMPLYRINFO", expectedOptions))
                .thenReturn(metadata);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", metadata.programKoreanName(), "crud", "analysis-1", "spec-1"))
                .thenReturn(screenSpecification);
        when(crudPromptBuilderService.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out",
                "5.0", "jsp", null, null, null, metadata, screenSpecification))
                .thenReturn("CLAUDE_PROMPT");

        String result = tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "claude",
                null, null, null, null, null,
                null, null, null, null, "analysis-1", "spec-1");

        assertThat(result).isEqualTo("CLAUDE_PROMPT");
        verify(generateCrudProjectUseCase, never()).execute(any());
    }

    @Test
    void buildFullCrudPrompt_auto_passesProgramMetadataAndLayoutOptionsThroughGenerationOptions() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                "EgovEmployerList", "/emp/list.do", "직원목록", "/emp/", null, null);
        CrudGenerationCommand expectedCommand = autoCommand(
                "5.0", "thymeleaf",
                new LayoutOptions("create", "layout/admin", "layout/admin-breadcrumb"),
                new ProgramMetadataOverrides("EgovEmployerList", "/emp/list.do", "직원목록", "/emp/"),
                DesignContextReference.empty());
        when(generateCrudProjectUseCase.execute(expectedCommand))
                .thenReturn(new CrudOrchestrationResult(false, "com", "LETTNEMPLYRINFO", "Employer",
                        "/tmp/out", List.of("EgovEmployerList.html"), List.of(), "OK", "OK"));

        String result = tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "auto",
                "5.0", "thymeleaf", "create", "layout/admin", "layout/admin-breadcrumb",
                "EgovEmployerList", "/emp/list.do", "직원목록", "/emp/", null, null);

        assertThat(result).contains("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===");
        verify(generateCrudProjectUseCase).execute(expectedCommand);
        assertThat(expectedCommand.toGenerationOptions()).isEqualTo(expectedOptions);
    }

    @Test
    void buildFullCrudPrompt_claude_passesProgramMetadataOverridesAndLayoutOptionsThrough() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                "EgovEmployerList", "/emp/list.do", "직원목록", "/emp/", null, null);
        CrudProgramMetadata metadata = new CrudProgramMetadata(
                "EgovEmployerList", "/emp/", "직원목록", null, Map.of(), null,
                CrudProgramMetadata.Source.EXPLICIT, CrudProgramMetadata.Status.RESOLVED, null);
        ScreenSpecification screenSpecification = new ScreenSpecification(
                "spec-2", 1, ScreenSpecStatus.APPROVED, "직원목록", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        when(crudProgramMetadataService.resolve("com", "Employer", "LETTNEMPLYRINFO", expectedOptions))
                .thenReturn(metadata);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", "직원목록", "crud", null, null))
                .thenReturn(screenSpecification);
        when(crudPromptBuilderService.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out",
                "5.0", "thymeleaf", "create", "layout/admin", "layout/admin-breadcrumb",
                metadata, screenSpecification))
                .thenReturn("CLAUDE_PROMPT_WITH_METADATA");

        String result = tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "claude",
                "5.0", "thymeleaf", "create", "layout/admin", "layout/admin-breadcrumb",
                "EgovEmployerList", "/emp/list.do", "직원목록", "/emp/", null, null);

        assertThat(result).isEqualTo("CLAUDE_PROMPT_WITH_METADATA");
        verify(crudProgramMetadataService).resolve("com", "Employer", "LETTNEMPLYRINFO", expectedOptions);
        verify(crudPromptBuilderService).buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out",
                "5.0", "thymeleaf", "create", "layout/admin", "layout/admin-breadcrumb",
                metadata, screenSpecification);
    }

    @Test
    void buildFullCrudPrompt_claude_designSpecResolutionThrows_propagatesUncaught() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                null, null, null, null, "analysis-1", "spec-1");
        CrudProgramMetadata metadata = CrudProgramMetadata.fallback("fallback");
        when(crudProgramMetadataService.resolve("com", "Employer", "LETTNEMPLYRINFO", expectedOptions))
                .thenReturn(metadata);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", metadata.programKoreanName(), "crud", "analysis-1", "spec-1"))
                .thenThrow(new IllegalStateException(
                        "APPROVED 화면명세만 코드 생성에 사용할 수 있습니다: spec-1 (REVIEW_REQUIRED)"));

        assertThatThrownBy(() -> tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "claude",
                null, null, null, null, null,
                null, null, null, null, "analysis-1", "spec-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVIEW_REQUIRED");

        verify(crudPromptBuilderService, never()).buildFullCrudPrompt(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(CrudProgramMetadata.class), any());
    }

    /** buildFullCrudPrompt(llmProvider="auto")가 Facade에서 조립하는 Command 형태. */
    private static CrudGenerationCommand autoCommand(
            String egovVersion, String viewType, LayoutOptions layout,
            ProgramMetadataOverrides program, DesignContextReference designContext) {
        return new CrudGenerationCommand(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                java.nio.file.Path.of("/tmp/out"), "auto", egovVersion, viewType,
                layout, program, designContext);
    }
}
