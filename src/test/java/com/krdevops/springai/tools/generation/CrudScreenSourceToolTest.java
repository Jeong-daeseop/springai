package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.mcp.ScreenSourceResultFormatter;
import com.krdevops.springai.service.generation.source.CrudScreenSourceGenerator;
import com.krdevops.springai.service.generation.source.ScreenSourceGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 CRUD 단일 화면 미리보기 MCP 진입점. {@link ScreenSourceMcpFacade}(실제 구현, 하위
 * {@link CrudScreenSourceGenerator}까지 실제 객체)를 통해 리팩터링 전과 동일한 결과를 반환하는지 검증한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class CrudScreenSourceToolTest {

    @Mock CrudSchemaQueryService crudSchemaQueryService;
    @Mock CrudModelFactory crudModelFactory;
    @Mock CrudTemplateRenderer crudTemplateRenderer;

    CrudScreenSourceTool tool;

    private static final FieldModel ID_FIELD = new FieldModel(
            "ID", "id", "String", "식별자", true, true, true, 20, "VARCHAR");

    @BeforeEach
    void setUp() {
        CrudScreenSourceGenerator crudGenerator = new CrudScreenSourceGenerator(
                crudSchemaQueryService, crudModelFactory, crudTemplateRenderer);
        ScreenSourceGenerationService generationService =
                new ScreenSourceGenerationService(List.of(crudGenerator));
        ScreenSourceMcpFacade facade =
                new ScreenSourceMcpFacade(generationService, new ScreenSourceResultFormatter());
        tool = new CrudScreenSourceTool(facade);
    }

    private CrudTemplateModel crudModel(String domain, String domainLc, String tableName, PkModel pk) {
        return new CrudTemplateModel(
                "egovframework.let.emp", domain, domainLc, "직원", tableName,
                "/emp/" + domainLc, "2026-07-01", "5.0", true,
                pk, List.of(ID_FIELD), List.of(ID_FIELD), List.of(ID_FIELD), List.of(), List.of());
    }

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
}
