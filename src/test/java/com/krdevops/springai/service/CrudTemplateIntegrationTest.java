package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FreeMarker CRUD 템플릿 통합 검증.
 * 11개 레이어 전체를 렌더링하여 핵심 내용 및 미치환 변수 잔존 여부를 확인한다.
 */
@SpringBootTest
class CrudTemplateIntegrationTest {

    @Autowired
    CrudTemplateRenderer renderer;

    // ─── 공통 픽스처 ──────────────────────────────────────────────────────────

    private static final PkModel PK = new PkModel("EMPLYR_ID", "emplyrId", "String");

    private static final List<FieldModel> FIELDS = List.of(
        new FieldModel("EMPLYR_ID", "emplyrId", "String",  "직원ID",   true,  true,  true,  20,   "VARCHAR"),
        new FieldModel("USER_NM",   "userNm",   "String",  "사용자명", false, true,  true,  50,   "VARCHAR"),
        new FieldModel("ESNTL_ID",  "esntlId",  "String",  "고유ID",   false, true,  true,  50,   "VARCHAR"),
        new FieldModel("AGE",       "age",       "Integer", "나이",     false, false, false, null, "INTEGER")
    );

    private static final List<FieldModel> NON_PK = FIELDS.stream().filter(f -> !f.pk()).toList();

    private static final CrudTemplateModel MODEL_50 = new CrudTemplateModel(
        "egovframework.let.emp",
        "Employer",
        "employer",
        "직원",
        "COMTNEMPLYRINFO",
        "/emp/employer",
        "2026-06-15",
        "5.0",
        true,
        PK,
        FIELDS,
        NON_PK
    );

    private static final CrudTemplateModel MODEL_43 = new CrudTemplateModel(
        "egovframework.let.emp",
        "Employer",
        "employer",
        "직원",
        "COMTNEMPLYRINFO",
        "/emp/employer",
        "2026-06-15",
        "4.3",
        false,
        PK,
        FIELDS,
        NON_PK
    );

    // ─── VO ───────────────────────────────────────────────────────────────────

    @Test
    void vo_50_classDeclarationAndImports() {
        String result = renderer.renderByLayerKey("vo", MODEL_50);

        assertThat(result).contains("package egovframework.let.emp.service;");
        assertThat(result).contains("public class EmployerVO");
        assertThat(result).contains("import jakarta.validation.constraints.NotBlank;");
        assertThat(result).contains("import lombok.Getter;");
        assertThat(result).contains("PaginationInfo paginationInfo");
    }

    @Test
    void vo_43_usesJavaxValidation() {
        String result = renderer.renderByLayerKey("vo", MODEL_43);

        assertThat(result).contains("import javax.validation.constraints.NotBlank;");
        assertThat(result).doesNotContain("jakarta.validation");
    }

    @Test
    void vo_noUnresolvedFreeMarkerVariables() {
        String result = renderer.renderByLayerKey("vo", MODEL_50);

        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }

    // ─── Controller ───────────────────────────────────────────────────────────

    @Test
    void controller_containsEgovFrameRequirements() {
        String result = renderer.renderByLayerKey("controller", MODEL_50);

        assertThat(result).contains("@Controller");
        assertThat(result).contains("EgovPropertyService propertiesService");
        assertThat(result).contains("ModelMap model");
        assertThat(result).contains("PaginationInfo paginationInfo = new PaginationInfo()");
        assertThat(result).contains("class EgovEmployerController");
    }

    @Test
    void controller_noUnresolvedFreeMarkerVariables() {
        String result = renderer.renderByLayerKey("controller", MODEL_50);

        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }

    // ─── Service / ServiceImpl ────────────────────────────────────────────────

    @Test
    void service_containsInterfaceDeclaration() {
        String result = renderer.renderByLayerKey("service", MODEL_50);

        assertThat(result).contains("public interface EmployerService");
        assertThat(result).contains("List<EmployerVO> selectEmployerList");
        assertThat(result).contains("int selectEmployerListTotCnt");
    }

    @Test
    void serviceImpl_extendsEgovAbstractServiceImpl() {
        String result = renderer.renderByLayerKey("serviceImpl", MODEL_50);

        assertThat(result).contains("extends EgovAbstractServiceImpl");
        assertThat(result).contains("@Transactional");
        assertThat(result).contains("class EgovEmployerServiceImpl");
    }

    // ─── Mapper ───────────────────────────────────────────────────────────────

    @Test
    void mapper_extendsEgovAbstractMapper() {
        String result = renderer.renderByLayerKey("mapper", MODEL_50);

        assertThat(result).contains("extends EgovAbstractMapper");
        assertThat(result).contains("@Mapper");
        assertThat(result).contains("interface EmployerMapper");
    }

    // ─── Mapper XML ───────────────────────────────────────────────────────────

    @Test
    void mapperXml_allSixSqlIds() {
        String result = renderer.renderByLayerKey("mapperXml", MODEL_50);

        assertThat(result)
            .contains("id=\"selectEmployerList\"")
            .contains("id=\"selectEmployerListTotCnt\"")
            .contains("id=\"selectEmployer\"")
            .contains("id=\"insertEmployer\"")
            .contains("id=\"updateEmployer\"")
            .contains("id=\"deleteEmployer\"");
    }

    @Test
    void mapperXml_pagination_usesFirstRecordIndex() {
        String result = renderer.renderByLayerKey("mapperXml", MODEL_50);

        assertThat(result).contains("#{paginationInfo.firstRecordIndex}");
    }

    @Test
    void mapperXml_resultMap_idAndResultTags() {
        String result = renderer.renderByLayerKey("mapperXml", MODEL_50);

        assertThat(result).contains("<id property=\"emplyrId\"");
        assertThat(result).contains("<result property=\"userNm\"");
    }

    @Test
    void mapperXml_noUnresolvedFreeMarkerVariables() {
        String result = renderer.renderByLayerKey("mapperXml", MODEL_50);

        // MyBatis #{...} 는 정상 출력이므로 FreeMarker ${...} 만 검사
        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }

    // ─── ControllerAdvice ─────────────────────────────────────────────────────

    @Test
    void controllerAdvice_classDeclaration() {
        String result = renderer.renderByLayerKey("controlleradvice", MODEL_50);

        assertThat(result).contains("@ControllerAdvice(assignableTypes = EgovEmployerController.class)");
        assertThat(result).contains("class EgovEmployerValidationHandler");
        assertThat(result).contains("MethodArgumentNotValidException");
    }

    // ─── JSP 템플릿 ───────────────────────────────────────────────────────────

    @Test
    void jspList_containsTableHeadersAndEgovPagination() {
        String result = renderer.renderByLayerKey("jspList", MODEL_50);

        // 컬럼 코멘트가 <th>로 생성됐는지 확인
        assertThat(result).contains("<th>직원ID</th>");
        assertThat(result).contains("<th>사용자명</th>");
        // JSP EL이 이스케이프되어 출력됐는지 확인
        assertThat(result).contains("${resultList}");
        assertThat(result).contains("ui:pagination");
    }

    @Test
    void jspList_dynamicTdCells_usesCout() {
        String result = renderer.renderByLayerKey("jspList", MODEL_50);

        assertThat(result).contains("<c:out value=\"${item.emplyrId}\"");
        assertThat(result).contains("<c:out value=\"${item.userNm}\"");
    }

    @Test
    void jspDetail_containsAllFieldRows() {
        String result = renderer.renderByLayerKey("jspDetail", MODEL_50);

        assertThat(result).contains("<th>직원ID</th>");
        assertThat(result).contains("<c:out value=\"${result.emplyrId}\"");
        assertThat(result).contains("<c:out value=\"${result.userNm}\"");
    }

    @Test
    void jspRegist_nonPkFormInputs() {
        String result = renderer.renderByLayerKey("jspRegist", MODEL_50);

        // eGov 테이블은 PK가 사용자 입력이므로 등록 폼에 포함
        assertThat(result).contains("path=\"emplyrId\"");
        // nonPK 필드도 form:input으로 생성
        assertThat(result).contains("path=\"userNm\"");
        assertThat(result).contains("path=\"age\"");
    }

    @Test
    void jspUpdt_containsHiddenPkAndNonPkInputs() {
        String result = renderer.renderByLayerKey("jspUpdt", MODEL_50);

        assertThat(result).contains("<form:hidden path=\"emplyrId\"");
        assertThat(result).contains("path=\"userNm\"");
    }

    // ─── 11개 전체 레이어 미치환 검증 ────────────────────────────────────────

    @Test
    void allJavaLayers_noUnresolvedFreeMarkerSyntax() {
        String[] javaLayers = {"vo", "controller", "service", "serviceImpl",
                               "mapper", "controlleradvice"};

        for (String layer : javaLayers) {
            String result = renderer.renderByLayerKey(layer, MODEL_50);
            assertThat(result)
                .as("레이어 [%s]에 미치환 FreeMarker 구문 잔존", layer)
                .doesNotContain("<#")
                .doesNotContain("${");
        }
    }

    @Test
    void mapperXml_noUnresolvedSyntax() {
        String result = renderer.renderByLayerKey("mapperXml", MODEL_50);

        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }
}
