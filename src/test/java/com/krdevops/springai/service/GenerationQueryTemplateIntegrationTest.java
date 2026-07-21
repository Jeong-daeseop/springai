package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardDisplayModel;
import com.krdevops.springai.model.board.BoardRouteModel;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GenerationQueryTemplateIntegrationTest {

    @Autowired CrudTemplateRenderer crudRenderer;
    @Autowired BoardTemplateRenderer boardRenderer;
    @Autowired MasterDetailTemplateRenderer masterDetailRenderer;

    private final GenerationQueryContractFactory contractFactory = new GenerationQueryContractFactory();

    @Test
    void crudRendersProjectionResultMapVoAndJoin() {
        CrudTemplateModel model = crudModel("t");

        assertThat(crudRenderer.renderByLayerKey("vo", model))
                .contains("private String department;")
                .contains("private String status;");
        assertThat(crudRenderer.renderByLayerKey("mapperXml", model))
                .contains("dept.ORGNZT_NM AS department")
                .contains("cc1.CODE_NM AS status")
                .contains("LEFT JOIN com.COMTNORGNZTINFO dept ON t.ORGNZT_ID = dept.ORGNZT_ID")
                .contains("<result property=\"department\" column=\"department\"/>");
    }

    @Test
    void boardUsesBoardPrimaryAliasForGeneratedJoins() {
        List<FieldModel> fields = GenerationQueryContractFactoryTest.physicalFields();
        FieldModel bbsId = new FieldModel("BBS_ID", "bbsId", "String", "게시판ID", true, true, true, 20, "VARCHAR");
        FieldModel nttId = new FieldModel("NTT_ID", "nttId", "Long", "게시글ID", true, true, false, null, "BIGINT");
        List<FieldModel> boardFields = new java.util.ArrayList<>(List.of(bbsId, nttId));
        boardFields.addAll(fields.subList(1, fields.size()));
        var contract = contractFactory.create(
                GenerationQueryContractFactoryTest.specification(), boardFields, "b");
        BoardTemplateModel model = new BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "게시판", "EMPLOYEE",
                "LETTNBBSMASTER", null, "/bbs/bbs", "2026-07-17", "5.0", true,
                bbsId, nttId, false, null, null, boardFields,
                contract.displayFields(), boardFields, List.of(), List.of(), false,
                new BoardDisplayModel(null, "게시판", null),
                new BoardRouteModel("/bbs/bbs", null, null, null), contract);

        String mapper = boardRenderer.renderByLayerKey("mapperXml", model);

        assertThat(mapper)
                .contains("b.ORGNZT_ID = dept.ORGNZT_ID")
                .contains("b.STATUS_CODE = cc1.CODE")
                .contains("dept.ORGNZT_NM AS department");
        assertThat(boardRenderer.renderByLayerKey("vo", model))
                .contains("private String department;");
    }

    @Test
    void masterDetailMasterMapperUsesSameQueryContract() {
        CrudTemplateModel master = crudModel("t");
        CrudTemplateModel detail = new CrudTemplateModel(
                "egovframework.let.emp", "Detail", "detail", "상세", "DETAIL",
                "/emp/detail", "2026-07-17", "5.0", true,
                new PkModel("ID", "id", "Long"), List.of(master.fields().get(0)),
                List.of(master.fields().get(0)), List.of(master.fields().get(0)), List.of(), List.of());
        MasterDetailTemplateModel model = new MasterDetailTemplateModel(master, detail, "ID", "id");

        assertThat(masterDetailRenderer.renderByLayerKey("masterMapperXml", model))
                .contains("dept.ORGNZT_NM AS department")
                .contains("LEFT JOIN com.COMTNORGNZTINFO dept");
        assertThat(masterDetailRenderer.renderByLayerKey("masterVo", model))
                .contains("private String department;");
    }

    private CrudTemplateModel crudModel(String primaryAlias) {
        List<FieldModel> fields = GenerationQueryContractFactoryTest.physicalFields();
        var contract = contractFactory.create(
                GenerationQueryContractFactoryTest.specification(), fields, primaryAlias);
        List<FieldModel> listFields = new java.util.ArrayList<>(List.of(fields.get(0)));
        listFields.addAll(contract.displayFields());
        return new CrudTemplateModel(
                "egovframework.let.emp", "Employee", "employee", "직원", "EMPLOYEE",
                "/emp/employee", "2026-07-17", "5.0", true,
                new PkModel("ID", "id", "Long"), List.of(fields.get(0)), fields, listFields,
                fields.subList(1, fields.size()), fields.subList(1, fields.size()),
                CrudRouteModel.canonicalOnly("/emp/employee"), contract);
    }
}
