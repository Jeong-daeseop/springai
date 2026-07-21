package com.krdevops.springai.tools;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.MasterDetailOrchestrationService;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.GenerationDesignContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrudPromptBuilderToolTest {

    @Mock CrudOrchestrationService crudOrchestrationService;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock CrudTemplateRenderer crudTemplateRenderer;
    @Mock CrudPromptBuilderService crudPromptBuilderService;
    @Mock MasterDetailService masterDetailService;
    @Mock MasterDetailTemplateRenderer masterDetailTemplateRenderer;
    @Mock MasterDetailOrchestrationService masterDetailOrchestrationService;
    @Mock BoardSchemaService boardSchemaService;
    @Mock BoardModelFactory boardModelFactory;
    @Mock BoardTemplateRenderer boardTemplateRenderer;
    @Mock BoardOrchestrationService boardOrchestrationService;
    @Mock BoardTableSetResolver boardTableSetResolver;
    @Mock BoardProgramMetadataService boardProgramMetadataService;
    @Mock GenerationDesignContextService generationDesignContextService;

    @InjectMocks
    CrudPromptBuilderTool tool;

    private static final FieldModel ID_FIELD = new FieldModel(
            "ID", "id", "String", "식별자", true, true, true, 20, "VARCHAR");
    private static final FieldModel BBS_ID_FIELD = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, true, true, 20, "VARCHAR");
    private static final FieldModel NTT_ID_FIELD = new FieldModel(
            "NTT_ID", "nttId", "Long", "게시글번호", true, true, false, null, "BIGINT");

    @Test
    void generateCrudList_returnsThymeleafPathAndCode() {
        CrudTemplateModel model = new CrudTemplateModel(
                "egovframework.let.emp",
                "Employer",
                "employer",
                "직원",
                "LETTNEMPLYRINFO",
                "/emp/employer",
                "2026-07-01",
                "5.0",
                true,
                new PkModel("ID", "id", "String"),
                List.of(ID_FIELD),
                List.of(ID_FIELD),
                List.of(ID_FIELD),
                List.of(),
                List.of());
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema("LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0",
                List.of(Map.of("COLUMN_NAME", "ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.THYMELEAF, ScreenSubsetMode.LIST_AND_DETAIL, null))
                .thenReturn(model);
        when(crudTemplateRenderer.renderByLayerKey("thymeleafList", model))
                .thenReturn("<html>crud-list</html>");

        String result = tool.generateCrudList(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/out", "5.0", "thymeleaf");

        assertThat(result).contains("featureType: CRUD");
        assertThat(result).contains("screen: List");
        assertThat(result).contains("layerKey: thymeleafList");
        assertThat(result).contains("/tmp/out/src/main/resources/templates/employer/EgovEmployerList.html");
        assertThat(result).contains("<html>crud-list</html>");
    }

    @Test
    void generateBoardList_usesDefaultTablesAndReturnsJspPath() {
        BoardTemplateModel model = new BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "BBS",
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "/bbs/bbs", "2026-07-01", "5.0", true,
                BBS_ID_FIELD, NTT_ID_FIELD,
                false, null, "LETTNFILEDETAIL",
                List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD),
                false);
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardProgramMetadataService.resolve(eq("com"), eq("Bbs"), eq("LETTNBBSMASTER"), any()))
                .thenReturn(BoardProgramMetadata.fallback("fallback"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        when(boardModelFactory.fromSchemas("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILEDETAIL",
                "Bbs", "egovframework.let.bbs", "5.0",
                Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))),
                BoardProgramMetadata.fallback("fallback")))
                .thenReturn(model);
        when(boardTemplateRenderer.renderByLayerKey("jspList", model))
                .thenReturn("<jsp>board-list</jsp>");

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null);

        verify(boardSchemaService).fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER",
                "LETTNBBSUSE", "LETTNFILE", "LETTNFILEDETAIL");
        assertThat(result).contains("featureType: BOARD");
        assertThat(result).contains("layerKey: jspList");
        assertThat(result).contains("/tmp/out/src/main/webapp/WEB-INF/jsp/bbs/EgovBbsList.jsp");
        assertThat(result).contains("<jsp>board-list</jsp>");
    }

    @Test
    void buildBoardFeaturePassesExplicitMetadataOptions() {
        when(boardOrchestrationService.orchestrate(any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new BoardOrchestrationResult(false, "let", "LETTNBBS", "InfoNotice", "/tmp/out",
                        List.of(), List.of(), "OK", "OK"));

        tool.buildBoardFeature("let", "InfoNotice", "egovframework.let.cop.bbs", "/tmp/out",
                null, null, null, null, null, "5.0", "thymeleaf", "reuse", null, null,
                "EgovInfoNotice", "/cop/bbs/list.do?bbsId=BBS_NOTICE", "공지사항",
                "/cop/bbs/", "BBS_NOTICE");

        verify(boardOrchestrationService).orchestrate(
                eq("let"), eq("InfoNotice"), eq("egovframework.let.cop.bbs"), eq("/tmp/out"),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq("5.0"), eq("thymeleaf"),
                eq("reuse"), eq(null), eq(null),
                eq(new BoardGenerationOptions("EgovInfoNotice", "/cop/bbs/list.do?bbsId=BBS_NOTICE",
                        "공지사항", "/cop/bbs/", "BBS_NOTICE")));
    }

    @Test
    void generateMasterDetail_returnsDetailScreenPath() {
        CrudTemplateModel masterModel = new CrudTemplateModel(
                "egovframework.let.bbs",
                "BbsMaster",
                "bbsMaster",
                "게시판마스터",
                "LETTNBBSMASTER",
                "/bbs/bbsMaster",
                "2026-07-01",
                "5.0",
                true,
                new PkModel("BBS_ID", "bbsId", "String"),
                List.of(BBS_ID_FIELD),
                List.of(BBS_ID_FIELD),
                List.of(BBS_ID_FIELD),
                List.of(),
                List.of());
        CrudTemplateModel detailModel = new CrudTemplateModel(
                "egovframework.let.bbs",
                "BbsUse",
                "bbsUse",
                "게시판사용",
                "LETTNBBSUSE",
                "/bbs/bbsMaster",
                "2026-07-01",
                "5.0",
                true,
                new PkModel("BBS_ID", "bbsId", "String"),
                List.of(BBS_ID_FIELD),
                List.of(BBS_ID_FIELD),
                List.of(BBS_ID_FIELD),
                List.of(),
                List.of());
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudModelFactory.fromSchema("LETTNBBSMASTER", "BbsMaster", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.THYMELEAF, ScreenSubsetMode.NONE, null))
                .thenReturn(masterModel);
        when(crudModelFactory.fromSchema("LETTNBBSUSE", "Bbsuse", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.THYMELEAF, ScreenSubsetMode.NONE, null))
                .thenReturn(detailModel);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("thymeleafDetail"), any(MasterDetailTemplateModel.class)))
                .thenReturn("<html>master-detail</html>");

        String result = tool.generateMasterDetail(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", "5.0", "thymeleaf");

        assertThat(result).contains("featureType: MASTER_DETAIL");
        assertThat(result).contains("layerKey: thymeleafDetail");
        assertThat(result).contains("/tmp/out/src/main/resources/templates/bbsMaster/EgovBbsMasterDetail.html");
        assertThat(result).contains("<html>master-detail</html>");
        verify(masterDetailTemplateRenderer).renderByLayerKey(eq("thymeleafDetail"), any(MasterDetailTemplateModel.class));
    }

    // ── designReferenceId / screenSpecificationId 배선 회귀 테스트 ──────────────
    // local-vision-design-reference-integration-review.md §5가 지적한 auto/claude
    // 중복 분기 지점 — 두 provider 모두에서 실제로 배선되는지 확인한다.

    @Test
    void buildFullCrudPrompt_auto_passesDesignReferenceIdsThroughGenerationOptions() {
        CrudGenerationOptions expectedOptions = new CrudGenerationOptions(
                null, null, null, null, "analysis-1", "spec-1");
        when(crudOrchestrationService.orchestrate(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out",
                "5.0", "jsp", null, null, null, expectedOptions))
                .thenReturn(new CrudOrchestrationResult(false, "com", "LETTNEMPLYRINFO", "Employer",
                        "/tmp/out", List.of("EgovEmployerList.html"), List.of(), "OK", "OK"));

        String result = tool.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", "auto",
                null, null, null, null, null,
                null, null, null, null, "analysis-1", "spec-1");

        assertThat(result).contains("=== [auto] eGovFrame 5.x CRUD 소스 생성 완료 ===");
        verify(crudOrchestrationService).orchestrate(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out",
                "5.0", "jsp", null, null, null, expectedOptions);
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
        verify(crudOrchestrationService, never()).orchestrate(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(CrudGenerationOptions.class));
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

    @Test
    void buildMasterDetailPrompt_auto_passesResolvedScreenSpecificationToOrchestrator() {
        ScreenSpecification screenSpecification = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "직원목록", "master-detail", "MASTER_DETAIL_LIST",
                "com", "LETTNEMPLYRINFO", List.of(), List.of(), List.of(), null);
        when(generationDesignContextService.resolve(
                "com", "LETTNEMPLYRINFO", "Employer", "master-detail", "analysis-1", "spec-1"))
                .thenReturn(screenSpecification);
        when(masterDetailOrchestrationService.orchestrate(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", "5.0", "jsp", null, null, null, screenSpecification))
                .thenReturn(new MasterDetailOrchestrationResult(false, "com", "LETTNEMPLYRINFO",
                        "LETTNEMPLYRATTRBINFO", "Employer", "/tmp/out",
                        List.of("EgovEmployerDetail.html"), List.of(), "OK", "OK"));

        String result = tool.buildMasterDetailPrompt(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", null, null, "auto", null, null, null,
                "analysis-1", "spec-1");

        assertThat(result).contains("=== [auto] eGovFrame 마스터-디테일 CRUD 소스 생성 완료 ===");
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
        verify(masterDetailOrchestrationService, never()).orchestrate(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(ScreenSpecification.class));
    }

    @Test
    void buildBoardFeature_passesDesignReferenceIdsThroughGenerationOptions() {
        BoardGenerationOptions expectedOptions = new BoardGenerationOptions(
                null, null, null, null, null, "analysis-1", "spec-1");
        when(boardOrchestrationService.orchestrate(
                eq("let"), eq("InfoNotice"), eq("egovframework.let.cop.bbs"), eq("/tmp/out"),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq("5.0"), eq("thymeleaf"),
                eq("reuse"), eq(null), eq(null), eq(expectedOptions)))
                .thenReturn(new BoardOrchestrationResult(false, "let", "LETTNBBS", "InfoNotice", "/tmp/out",
                        List.of(), List.of(), "OK", "OK"));

        tool.buildBoardFeature("let", "InfoNotice", "egovframework.let.cop.bbs", "/tmp/out",
                null, null, null, null, null, "5.0", "thymeleaf", "reuse", null, null,
                null, null, null, null, null, "analysis-1", "spec-1");

        verify(boardOrchestrationService).orchestrate(
                eq("let"), eq("InfoNotice"), eq("egovframework.let.cop.bbs"), eq("/tmp/out"),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq("5.0"), eq("thymeleaf"),
                eq("reuse"), eq(null), eq(null), eq(expectedOptions));
    }
}
