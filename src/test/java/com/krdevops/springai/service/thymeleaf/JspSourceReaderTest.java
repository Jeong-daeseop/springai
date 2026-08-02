package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.JspEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-2C 완료 게이트의 첫 단계: 골든 fixture(crud-jsp)에서 taglib·form·EL·forEach·표시 필드가
 * 기대한 대로 추출되는지 검증한다. 이 fixture들은 GenerationBaselineFixtureTest가 이미 사용 중이므로
 * 읽기만 하고 절대 수정하지 않는다.
 */
class JspSourceReaderTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final JspSourceReader reader = new JspSourceReader();

    @Test
    void listJspExtractsSearchFormForEachAndDisplayFields() throws IOException {
        JspEvidence evidence = read("EgovEmployerList.jsp");

        assertThat(evidence.taglibPrefixes()).containsEntry("c", "http://java.sun.com/jsp/jstl/core");
        assertThat(evidence.taglibPrefixes()).containsEntry("ui", "http://egovframework.gov/ctl/ui");

        assertThat(evidence.forms()).hasSize(1);
        var searchForm = evidence.forms().get(0);
        assertThat(searchForm.formName()).isEqualTo("searchForm");
        assertThat(searchForm.modelAttribute()).isNull();
        assertThat(searchForm.resolvedRoute()).isEqualTo("/emp/employerList.do");
        assertThat(searchForm.httpMethod()).isEqualTo("GET");
        assertThat(searchForm.fields()).extracting("fieldName")
                .containsExactlyInAnyOrder("pageIndex", "searchCondition", "searchKeyword");
        assertThat(searchForm.fields()).filteredOn(f -> f.fieldName().equals("pageIndex"))
                .allMatch(f -> f.hidden());

        assertThat(evidence.forEachBindings()).hasSize(1);
        assertThat(evidence.forEachBindings().get(0).loopVariable()).isEqualTo("item");
        assertThat(evidence.forEachBindings().get(0).itemsAttributeName()).isEqualTo("resultList");

        assertThat(evidence.displayFields())
                .extracting(f -> f.rootAttributeName() + "." + f.fieldName())
                .contains("item.emplyrId", "item.emplyrNm", "item.emailAdres", "item.ofcpsNm");

        assertThat(evidence.modelReferences()).extracting("attributeName")
                .contains("searchVO", "resultList", "paginationInfo", "message");

        assertThat(evidence.routeReferences()).contains(
                "/emp/employerList.do", "/emp/employerRegistView.do", "/emp/employerDetail.do");
    }

    @Test
    void registJspExtractsSpringFormBindingsAndErrorTags() throws IOException {
        JspEvidence evidence = read("EgovEmployerRegist.jsp");

        assertThat(evidence.forms()).hasSize(1);
        var form = evidence.forms().get(0);
        assertThat(form.modelAttribute()).isEqualTo("employerVO");
        assertThat(form.resolvedRoute()).isEqualTo("/emp/employerRegist.do");
        assertThat(form.httpMethod()).isEqualTo("POST");

        assertThat(form.fields()).extracting("fieldName")
                .containsExactlyInAnyOrder("emplyrId", "emplyrNm", "emailAdres", "ofcpsNm");
        assertThat(form.fields()).allMatch(f -> f.tagName().equals("form:input"));
        assertThat(form.fields()).allMatch(f -> f.errorDisplay());
    }

    @Test
    void detailJspExtractsDisplayFieldsAndDeleteForm() throws IOException {
        JspEvidence evidence = read("EgovEmployerDetail.jsp");

        assertThat(evidence.displayFields())
                .extracting(f -> f.rootAttributeName() + "." + f.fieldName())
                .contains("result.emplyrId", "result.emplyrNm", "result.emailAdres");

        assertThat(evidence.forms()).hasSize(1);
        var deleteForm = evidence.forms().get(0);
        assertThat(deleteForm.formName()).isEqualTo("deleteForm");
        assertThat(deleteForm.resolvedRoute()).isEqualTo("/emp/employerDelete.do");
        assertThat(deleteForm.httpMethod()).isEqualTo("POST");
        assertThat(deleteForm.fields()).extracting("fieldName").containsExactly("emplyrId");
    }

    private JspEvidence read(String fileName) throws IOException {
        String content = Files.readString(BASELINE.resolve(fileName));
        return reader.read(fileName, content);
    }
}
