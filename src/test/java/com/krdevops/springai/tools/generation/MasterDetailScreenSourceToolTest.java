package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.MasterDetailTemplateRenderer;
import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.mcp.ScreenSourceResultFormatter;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 마스터-디테일 단일 화면 미리보기 MCP 진입점. {@link ScreenSourceMcpFacade}(실제 구현, 하위
 * {@link MasterDetailScreenSourceGenerator}까지 실제 객체)를 통해 리팩터링 전과 동일한 결과를 반환하는지 검증한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class MasterDetailScreenSourceToolTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock MasterDetailTemplateRenderer masterDetailTemplateRenderer;

    MasterDetailScreenSourceTool tool;

    private static final FieldModel ID_FIELD = new FieldModel(
            "ID", "id", "String", "식별자", true, true, true, 20, "VARCHAR");

    @BeforeEach
    void setUp() {
        MasterDetailScreenSourceGenerator masterDetailGenerator = new MasterDetailScreenSourceGenerator(
                crudSchemaQueryService, crudModelFactory, masterDetailTemplateRenderer);
        ScreenSourceGenerationService generationService =
                new ScreenSourceGenerationService(List.of(masterDetailGenerator));
        ScreenSourceMcpFacade facade =
                new ScreenSourceMcpFacade(generationService, new ScreenSourceResultFormatter());
        tool = new MasterDetailScreenSourceTool(facade);
    }

    private CrudTemplateModel crudModel(String domain, String domainLc, String tableName, PkModel pk) {
        return new CrudTemplateModel(
                "egovframework.let.emp", domain, domainLc, "직원", tableName,
                "/emp/" + domainLc, "2026-07-01", "5.0", true,
                pk, List.of(ID_FIELD), List.of(ID_FIELD), List.of(ID_FIELD), List.of(), List.of());
    }

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
}
