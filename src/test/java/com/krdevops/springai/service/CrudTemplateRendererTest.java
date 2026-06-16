package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CrudTemplateRenderer — .ftl 파일 로딩 및 렌더링 검증.
 * FreeMarker record accessor 동작과 jakarta/javax 분기를 확인한다.
 */
@SpringBootTest
class CrudTemplateRendererTest {

    @Autowired
    CrudTemplateRenderer renderer;

    // ─── 픽스처 ───────────────────────────────────────────────────────────────

    private static final List<FieldModel> FIELDS = List.of(
        new FieldModel("EMPLYR_ID", "emplyrId", "String",  "직원ID",   true,  true,  true,  20,   "VARCHAR"),
        new FieldModel("USER_NM",   "userNm",   "String",  "사용자명", false, true,  true,  50,   "VARCHAR"),
        new FieldModel("AGE",       "age",       "Integer", "나이",     false, false, false, null, "INTEGER")
    );

    private static final List<FieldModel> NON_PK = FIELDS.stream().filter(f -> !f.pk()).toList();
    private static final PkModel PK = new PkModel("EMPLYR_ID", "emplyrId", "String");

    private CrudTemplateModel model(boolean jakarta) {
        return new CrudTemplateModel(
            "egovframework.let.emp",
            "Employer",
            "employer",
            "직원",
            "COMTNEMPLYRINFO",
            "/emp/employer",
            "2026-06-15",
            jakarta ? "5.0" : "4.3",
            jakarta,
            PK,
            FIELDS,
            NON_PK
        );
    }

    // ─── VO 렌더링 ────────────────────────────────────────────────────────────

    @Test
    void vo_jakarta50_containsJakartaImport() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("import jakarta.validation.constraints.NotBlank;");
        assertThat(result).contains("import jakarta.validation.constraints.NotNull;");
        assertThat(result).doesNotContain("import javax.validation");
    }

    @Test
    void vo_javax43_containsJavaxImport() {
        String result = renderer.renderByLayerKey("vo", model(false));

        assertThat(result).contains("import javax.validation.constraints.NotBlank;");
        assertThat(result).doesNotContain("import jakarta.validation");
    }

    @Test
    void vo_containsLombokAndPaginationInfo() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@Getter");
        assertThat(result).contains("@Setter");
        assertThat(result).contains("PaginationInfo paginationInfo");
    }

    @Test
    void vo_fieldsRendered_containsAllJavaFieldDeclarations() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("private String emplyrId;");
        assertThat(result).contains("private String userNm;");
        assertThat(result).contains("private Integer age;");
    }

    @Test
    void vo_requiredStringField_containsNotBlank() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@NotBlank");
    }

    @Test
    void vo_maxLength_containsSizeAnnotation() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@Size(max = 50)");
    }

    @Test
    void vo_noUnresolvedFreeMarkerSyntax() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }

    // ─── Controller 렌더링 ────────────────────────────────────────────────────

    @Test
    void controller_jakarta50_containsJakartaValidImport() {
        String result = renderer.renderByLayerKey("controller", model(true));

        assertThat(result).contains("import jakarta.validation.Valid;");
        assertThat(result).doesNotContain("import javax.validation.Valid");
    }

    @Test
    void controller_javax43_containsJavaxValidImport() {
        String result = renderer.renderByLayerKey("controller", model(false));

        assertThat(result).contains("import javax.validation.Valid;");
        assertThat(result).doesNotContain("import jakarta.validation.Valid");
    }

    @Test
    void controller_containsRequiredAnnotationsAndClasses() {
        String result = renderer.renderByLayerKey("controller", model(true));

        assertThat(result).contains("@Controller");
        assertThat(result).contains("EgovPropertyService");
        assertThat(result).contains("ModelMap");
        assertThat(result).contains("PaginationInfo");
    }

    // ─── Mapper XML 렌더링 ────────────────────────────────────────────────────

    @Test
    void mapperXml_containsSixSqlIds() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("id=\"selectEmployerList\"");
        assertThat(result).contains("id=\"selectEmployerListTotCnt\"");
        assertThat(result).contains("id=\"selectEmployer\"");
        assertThat(result).contains("id=\"insertEmployer\"");
        assertThat(result).contains("id=\"updateEmployer\"");
        assertThat(result).contains("id=\"deleteEmployer\"");
    }

    @Test
    void mapperXml_containsPaginationFirstRecordIndex() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("paginationInfo.firstRecordIndex");
        assertThat(result).contains("paginationInfo.recordCountPerPage");
    }

    @Test
    void mapperXml_containsResultMap() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("<resultMap");
        assertThat(result).contains("<id property=\"emplyrId\" column=\"EMPLYR_ID\"");
        assertThat(result).contains("<result property=\"userNm\" column=\"USER_NM\"");
    }

    // ─── 알 수 없는 layerKey ─────────────────────────────────────────────────

    @Test
    void renderByLayerKey_unknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.renderByLayerKey("unknown", model(true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }
}
