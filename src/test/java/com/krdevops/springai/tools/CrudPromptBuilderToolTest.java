package com.krdevops.springai.tools;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
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
import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.MasterDetailOrchestrationService;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.mcp.ScreenSourceResultFormatter;
import com.krdevops.springai.service.generation.source.BoardScreenSourceGenerator;
import com.krdevops.springai.service.generation.source.CrudScreenSourceGenerator;
import com.krdevops.springai.service.generation.source.MasterDetailScreenSourceGenerator;
import com.krdevops.springai.service.generation.source.ScreenSourceGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * WP-2: 12개 단일 화면 미리보기 {@code @Tool} 메서드가 {@code ScreenSourceMcpFacade}(실제 구현,
 * 하위 Use Case/Generator까지 실제 객체로 연결)를 통해 리팩터링 전과 동일한 결과를 반환하는지 검증한다.
 * buildFullCrudPrompt/buildMasterDetailPrompt/buildBoardFeature(WP-3 범위)는 기존 Mock 방식 그대로 둔다.
 */
@ExtendWith(MockitoExtension.class)
class CrudPromptBuilderToolTest {

    @Mock CrudOrchestrationService crudOrchestrationService;
    @Mock CrudProgramMetadataService crudProgramMetadataService;
    @Mock CrudPromptBuilderService crudPromptBuilderService;
    @Mock MasterDetailService masterDetailService;
    @Mock MasterDetailOrchestrationService masterDetailOrchestrationService;
    @Mock BoardOrchestrationService boardOrchestrationService;
    @Mock GenerationDesignContextService generationDesignContextService;

    // 단일 화면 미리보기 경로가 실제로 위임하는 하위 협력자 — Generator에 그대로 주입된다.
    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock CrudTemplateRenderer crudTemplateRenderer;
    @Mock BoardTableSetResolver boardTableSetResolver;
    @Mock BoardSchemaService boardSchemaService;
    @Mock BoardProgramMetadataService boardProgramMetadataService;
    @Mock BoardModelFactory boardModelFactory;
    @Mock BoardTemplateRenderer boardTemplateRenderer;
    @Mock MasterDetailTemplateRenderer masterDetailTemplateRenderer;

    CrudPromptBuilderTool tool;

    private static final FieldModel ID_FIELD = new FieldModel(
            "ID", "id", "String", "식별자", true, true, true, 20, "VARCHAR");
    private static final FieldModel BBS_ID_FIELD = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, true, true, 20, "VARCHAR");
    private static final FieldModel NTT_ID_FIELD = new FieldModel(
            "NTT_ID", "nttId", "Long", "게시글번호", true, true, false, null, "BIGINT");

    @BeforeEach
    void setUp() {
        CrudScreenSourceGenerator crudGenerator = new CrudScreenSourceGenerator(
                crudSchemaQueryService, crudModelFactory, crudTemplateRenderer);
        BoardScreenSourceGenerator boardGenerator = new BoardScreenSourceGenerator(
                boardTableSetResolver, boardSchemaService, boardProgramMetadataService,
                boardModelFactory, boardTemplateRenderer);
        MasterDetailScreenSourceGenerator masterDetailGenerator = new MasterDetailScreenSourceGenerator(
                crudSchemaQueryService, crudModelFactory, masterDetailTemplateRenderer);
        ScreenSourceGenerationService generationService = new ScreenSourceGenerationService(
                List.of(crudGenerator, boardGenerator, masterDetailGenerator));
        ScreenSourceMcpFacade facade = new ScreenSourceMcpFacade(generationService, new ScreenSourceResultFormatter());

        tool = new CrudPromptBuilderTool(
                crudOrchestrationService, crudProgramMetadataService, crudPromptBuilderService,
                masterDetailService, masterDetailOrchestrationService, boardOrchestrationService,
                generationDesignContextService, facade);
    }

    private CrudTemplateModel crudModel(String domain, String domainLc, String tableName, PkModel pk) {
        return new CrudTemplateModel(
                "egovframework.let.emp", domain, domainLc, "직원", tableName,
                "/emp/" + domainLc, "2026-07-01", "5.0", true,
                pk, List.of(ID_FIELD), List.of(ID_FIELD), List.of(ID_FIELD), List.of(), List.of());
    }

    // ── CRUD 단일 화면 ──────────────────────────────────────────────────────

    @Test
    void generateCrudList_returnsThymeleafPathAndCode() {
        CrudTemplateModel model = crudModel("Employer", "employer", "LETTNEMPLYRINFO",
                new PkModel("ID", "id", "String"));
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
    void generateCrudDetail_jsp_usesListOnlySubsetAndJspLayerKey() {
        CrudTemplateModel model = crudModel("Employer", "employer", "LETTNEMPLYRINFO",
                new PkModel("ID", "id", "String"));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema("LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0",
                List.of(Map.of("COLUMN_NAME", "ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.JSP, ScreenSubsetMode.LIST_ONLY, null))
                .thenReturn(model);
        when(crudTemplateRenderer.renderByLayerKey("jspDetail", model))
                .thenReturn("<jsp>crud-detail</jsp>");

        String result = tool.generateCrudDetail(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);

        assertThat(result).contains("screen: Detail");
        assertThat(result).contains("layerKey: jspDetail");
        assertThat(result).contains("/tmp/out/src/main/webapp/WEB-INF/jsp/employer/EgovEmployerDetail.jsp");
    }

    @Test
    void generateCrudRegist_returnsRegistLayerKey() {
        CrudTemplateModel model = crudModel("Employer", "employer", "LETTNEMPLYRINFO",
                new PkModel("ID", "id", "String"));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(model);
        when(crudTemplateRenderer.renderByLayerKey("jspRegist", model)).thenReturn("<jsp>crud-regist</jsp>");

        String result = tool.generateCrudRegist(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);

        assertThat(result).contains("screen: Regist");
        assertThat(result).contains("layerKey: jspRegist");
    }

    @Test
    void generateCrudUpdt_returnsUpdtLayerKey() {
        CrudTemplateModel model = crudModel("Employer", "employer", "LETTNEMPLYRINFO",
                new PkModel("ID", "id", "String"));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(model);
        when(crudTemplateRenderer.renderByLayerKey("jspUpdt", model)).thenReturn("<jsp>crud-updt</jsp>");

        String result = tool.generateCrudUpdt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);

        assertThat(result).contains("screen: Updt");
        assertThat(result).contains("layerKey: jspUpdt");
    }

    @Test
    void generateCrudList_tableNotFound_returnsKoreanMessageWithoutRendering() {
        when(crudSchemaQueryService.fetchColumns("com", "NOPE")).thenReturn(List.of());

        String result = tool.generateCrudList(
                "com", "NOPE", "Employer", "egovframework.let.emp", "/tmp/out", null, null);

        assertThat(result).isEqualTo("테이블을 찾을 수 없습니다: com.NOPE");
        verify(crudTemplateRenderer, never()).renderByLayerKey(any(), any());
    }

    // ── 게시판(BOARD) 단일 화면 ──────────────────────────────────────────────

    private com.krdevops.springai.model.board.BoardTemplateModel boardModel() {
        return new com.krdevops.springai.model.board.BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "BBS",
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "/bbs/bbs", "2026-07-01", "5.0", true,
                BBS_ID_FIELD, NTT_ID_FIELD,
                false, null, "LETTNFILEDETAIL",
                List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD),
                false);
    }

    private void stubBoardHappyPath(com.krdevops.springai.model.board.BoardTemplateModel model, String layerKey, String code) {
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
        when(boardTemplateRenderer.renderByLayerKey(layerKey, model)).thenReturn(code);
    }

    @Test
    void generateBoardList_usesDefaultTablesAndReturnsJspPath() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspList", "<jsp>board-list</jsp>");

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
    void generateBoardDetail_returnsDetailScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspDetail", "<jsp>board-detail</jsp>");

        String result = tool.generateBoardDetail(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Detail");
        assertThat(result).contains("layerKey: jspDetail");
    }

    @Test
    void generateBoardRegist_returnsRegistScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspRegist", "<jsp>board-regist</jsp>");

        String result = tool.generateBoardRegist(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Regist");
        assertThat(result).contains("layerKey: jspRegist");
    }

    @Test
    void generateBoardUpdt_returnsUpdtScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspUpdt", "<jsp>board-updt</jsp>");

        String result = tool.generateBoardUpdt(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Updt");
        assertThat(result).contains("layerKey: jspUpdt");
    }

    @Test
    void generateBoardList_tableNotFound_propagatesMessageFromSchemaService() {
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenThrow(new IllegalArgumentException("게시판 테이블을 찾을 수 없습니다: com.LETTNBBS"));

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null);

        assertThat(result).isEqualTo("게시판 테이블을 찾을 수 없습니다: com.LETTNBBS");
        verify(boardModelFactory, never()).fromSchemas(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateBoardList_blocksGeneration_returnsMetadataMessageWithoutRendering() {
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        BoardProgramMetadata ambiguous = new BoardProgramMetadata(null, null, null, null, null, null, null,
                BoardProgramMetadata.Source.DATABASE, BoardProgramMetadata.Status.AMBIGUOUS,
                "동일 도메인의 프로그램이 여러 건 발견되었습니다");
        when(boardProgramMetadataService.resolve(eq("com"), eq("Bbs"), eq("LETTNBBSMASTER"), any()))
                .thenReturn(ambiguous);

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null);

        assertThat(result).isEqualTo("동일 도메인의 프로그램이 여러 건 발견되었습니다");
        verify(boardModelFactory, never()).fromSchemas(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ── 마스터-디테일 단일 화면 ──────────────────────────────────────────────

    private CrudTemplateModel masterModel(PkModel pk) {
        return crudModel("BbsMaster", "bbsMaster", "LETTNBBSMASTER", pk);
    }

    private CrudTemplateModel detailModel(String domain, String domainLc) {
        return crudModel(domain, domainLc, "LETTNBBSUSE", new PkModel("BBS_ID", "bbsId", "String"));
    }

    @Test
    void generateMasterDetail_returnsDetailScreenPath() {
        CrudTemplateModel master = masterModel(new PkModel("BBS_ID", "bbsId", "String"));
        CrudTemplateModel detail = detailModel("Bbsuse", "bbsUse");
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudModelFactory.fromSchema("LETTNBBSMASTER", "BbsMaster", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.THYMELEAF, ScreenSubsetMode.NONE, null))
                .thenReturn(master);
        when(crudModelFactory.fromSchema("LETTNBBSUSE", "Bbsuse", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID")), CrudProgramMetadata.fallback(null),
                CrudViewType.THYMELEAF, ScreenSubsetMode.NONE, null))
                .thenReturn(detail);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("thymeleafDetail"), any(MasterDetailTemplateModel.class)))
                .thenReturn("<html>master-detail</html>");

        String result = tool.generateMasterDetail(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", "5.0", "thymeleaf");

        assertThat(result).contains("featureType: MASTER_DETAIL");
        assertThat(result).contains("layerKey: thymeleafDetail");
        assertThat(result).contains("/tmp/out/src/main/resources/templates/bbsMaster/EgovBbsMasterDetail.html");
        assertThat(result).contains("<html>master-detail</html>");
    }

    @Test
    void generateMasterList_regist_updt_returnScreenSpecificLayerKeys() {
        CrudTemplateModel master = masterModel(new PkModel("BBS_ID", "bbsId", "String"));
        CrudTemplateModel detail = detailModel("Bbsuse", "bbsUse");
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(master, detail, master, detail, master, detail);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("jspList"), any())).thenReturn("<jsp>list</jsp>");
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("jspRegist"), any())).thenReturn("<jsp>regist</jsp>");
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("jspUpdt"), any())).thenReturn("<jsp>updt</jsp>");

        String list = tool.generateMasterList(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);
        String regist = tool.generateMasterRegist(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);
        String updt = tool.generateMasterUpdt(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);

        assertThat(list).contains("screen: List").contains("layerKey: jspList");
        assertThat(regist).contains("screen: Regist").contains("layerKey: jspRegist");
        assertThat(updt).contains("screen: Updt").contains("layerKey: jspUpdt");
    }

    @Test
    void generateMasterDetail_fkColumnMatchesDetailColumnNamedLikeMasterPk() {
        CrudTemplateModel master = masterModel(new PkModel("BBS_ID", "bbsId", "String"));
        CrudTemplateModel detail = detailModel("Bbsuse", "bbsUse");
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID"), Map.of("COLUMN_NAME", "USE_YN")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(master, detail);
        ArgumentCaptor<MasterDetailTemplateModel> captor = ArgumentCaptor.forClass(MasterDetailTemplateModel.class);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("jspList"), captor.capture())).thenReturn("ok");

        tool.generateMasterList(
                "com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);

        assertThat(captor.getValue().fkColumn()).isEqualTo("BBS_ID");
    }

    @Test
    void generateMasterDetail_fkColumnFallsBackToMasterPkWhenNoNameMatch() {
        CrudTemplateModel master = masterModel(new PkModel("EMPLYR_ID", "emplyrId", "String"));
        CrudTemplateModel detail = detailModel("Emplyrattrbinfo", "emplyrattrbinfo");
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "EMPLYR_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRATTRBINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ATTRB_CD")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(master, detail);
        ArgumentCaptor<MasterDetailTemplateModel> captor = ArgumentCaptor.forClass(MasterDetailTemplateModel.class);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("jspList"), captor.capture())).thenReturn("ok");

        tool.generateMasterList(
                "com", "LETTNEMPLYRINFO", "LETTNEMPLYRATTRBINFO", "Employer",
                "egovframework.let.emp", "/tmp/out", null, null);

        assertThat(captor.getValue().fkColumn()).isEqualTo("EMPLYR_ID");
    }

    @Test
    void generateMasterList_masterTableNotFound_returnsKoreanMessage() {
        when(crudSchemaQueryService.fetchColumns("com", "NOPE")).thenReturn(List.of());

        String result = tool.generateMasterList(
                "com", "NOPE", "LETTNBBSUSE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);

        assertThat(result).isEqualTo("마스터 테이블을 찾을 수 없습니다: com.NOPE");
        verify(masterDetailTemplateRenderer, never()).renderByLayerKey(any(), any());
    }

    @Test
    void generateMasterList_detailTableNotFound_returnsKoreanMessage() {
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "NOPE")).thenReturn(List.of());

        String result = tool.generateMasterList(
                "com", "LETTNBBSMASTER", "NOPE", "BbsMaster", "egovframework.let.bbs", "/tmp/out", null, null);

        assertThat(result).isEqualTo("디테일 테이블을 찾을 수 없습니다: com.NOPE");
        verify(masterDetailTemplateRenderer, never()).renderByLayerKey(any(), any());
    }

    // ── 파일 미저장 보장 ─────────────────────────────────────────────────────
    // 단일 화면 미리보기 12개 메서드는 어떤 경로로도 CodeService에 도달하지 않는다.
    // CodeService는 *OrchestrationService/CrudPromptBuilderService/MasterDetailService에서만
    // 참조되므로, 이 협력자들이 전혀 호출되지 않았음을 검증하면 파일 미저장이 보장된다.

    @Test
    void singleScreenPreviewMethods_neverTouchFileWritingCollaborators() {
        when(crudSchemaQueryService.fetchColumns("com", "LETTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(crudModel("Employer", "employer", "LETTNEMPLYRINFO", new PkModel("ID", "id", "String")));
        when(crudTemplateRenderer.renderByLayerKey(any(), any())).thenReturn("code");

        var board = boardModel();
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardProgramMetadataService.resolve(eq("com"), eq("Bbs"), eq("LETTNBBSMASTER"), any()))
                .thenReturn(BoardProgramMetadata.fallback("fallback"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        when(boardModelFactory.fromSchemas(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(board);
        when(boardTemplateRenderer.renderByLayerKey(any(), any())).thenReturn("code");

        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "LETTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(masterDetailTemplateRenderer.renderByLayerKey(any(), any())).thenReturn("code");

        tool.generateCrudList("com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);
        tool.generateCrudDetail("com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);
        tool.generateCrudRegist("com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);
        tool.generateCrudUpdt("com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "/tmp/out", null, null);
        tool.generateBoardList("com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null);
        tool.generateBoardDetail("com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);
        tool.generateBoardRegist("com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);
        tool.generateBoardUpdt("com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);
        tool.generateMasterList("com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", null, null);
        tool.generateMasterDetail("com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", null, null);
        tool.generateMasterRegist("com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", null, null);
        tool.generateMasterUpdt("com", "LETTNBBSMASTER", "LETTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", null, null);

        // 이 협력자들의 실제 구현체만이 CodeService.saveGeneratedCode/saveGeneratedBinary에 도달한다.
        verifyNoInteractions(crudOrchestrationService);
        verifyNoInteractions(boardOrchestrationService);
        verifyNoInteractions(masterDetailOrchestrationService);
        verifyNoInteractions(crudPromptBuilderService);
        verifyNoInteractions(masterDetailService);
        verifyNoInteractions(generationDesignContextService);
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
