package com.krdevops.springai.tools;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardOrchestrationService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MasterDetailOrchestrationService;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrudPromptBuilderToolTest {

    @Mock CrudOrchestrationService crudOrchestrationService;
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
                "COMTNEMPLYRINFO",
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
        when(crudSchemaQueryService.fetchColumns("com", "COMTNEMPLYRINFO"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "ID")));
        when(crudModelFactory.fromSchema("COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0",
                List.of(Map.of("COLUMN_NAME", "ID"))))
                .thenReturn(model);
        when(crudTemplateRenderer.renderByLayerKey("thymeleafList", model))
                .thenReturn("<html>crud-list</html>");

        String result = tool.generateCrudList(
                "com", "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp",
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
                "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "/bbs/bbs", "2026-07-01", "5.0", true,
                BBS_ID_FIELD, NTT_ID_FIELD,
                false, null, "COMTNFILEDETAIL",
                List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD),
                false);
        when(boardSchemaService.fetchBoardSchemas("com", "COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE",
                "COMTNFILE", "COMTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        when(boardModelFactory.fromSchemas("COMTNBBS", "COMTNBBSMASTER", "COMTNBBSUSE", "COMTNFILEDETAIL",
                "Bbs", "egovframework.let.bbs", "5.0",
                Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID")))))
                .thenReturn(model);
        when(boardTemplateRenderer.renderByLayerKey("jspList", model))
                .thenReturn("<jsp>board-list</jsp>");

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null);

        verify(boardSchemaService).fetchBoardSchemas("com", "COMTNBBS", "COMTNBBSMASTER",
                "COMTNBBSUSE", "COMTNFILE", "COMTNFILEDETAIL");
        assertThat(result).contains("featureType: BOARD");
        assertThat(result).contains("layerKey: jspList");
        assertThat(result).contains("/tmp/out/src/main/webapp/WEB-INF/jsp/bbs/EgovBbsList.jsp");
        assertThat(result).contains("<jsp>board-list</jsp>");
    }

    @Test
    void generateMasterDetail_returnsDetailScreenPath() {
        CrudTemplateModel masterModel = new CrudTemplateModel(
                "egovframework.let.bbs",
                "BbsMaster",
                "bbsMaster",
                "게시판마스터",
                "COMTNBBSMASTER",
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
                "COMTNBBSUSE",
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
        when(crudSchemaQueryService.fetchColumns("com", "COMTNBBSMASTER"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudSchemaQueryService.fetchColumns("com", "COMTNBBSUSE"))
                .thenReturn(List.of(Map.of("COLUMN_NAME", "BBS_ID")));
        when(crudModelFactory.fromSchema("COMTNBBSMASTER", "BbsMaster", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID"))))
                .thenReturn(masterModel);
        when(crudModelFactory.fromSchema("COMTNBBSUSE", "Bbsuse", "egovframework.let.bbs", "5.0",
                List.of(Map.of("COLUMN_NAME", "BBS_ID"))))
                .thenReturn(detailModel);
        when(masterDetailTemplateRenderer.renderByLayerKey(eq("thymeleafDetail"), any(MasterDetailTemplateModel.class)))
                .thenReturn("<html>master-detail</html>");

        String result = tool.generateMasterDetail(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE", "BbsMaster",
                "egovframework.let.bbs", "/tmp/out", "5.0", "thymeleaf");

        assertThat(result).contains("featureType: MASTER_DETAIL");
        assertThat(result).contains("layerKey: thymeleafDetail");
        assertThat(result).contains("/tmp/out/src/main/resources/templates/bbsMaster/EgovBbsMasterDetail.html");
        assertThat(result).contains("<html>master-detail</html>");
        verify(masterDetailTemplateRenderer).renderByLayerKey(eq("thymeleafDetail"), any(MasterDetailTemplateModel.class));
    }
}
