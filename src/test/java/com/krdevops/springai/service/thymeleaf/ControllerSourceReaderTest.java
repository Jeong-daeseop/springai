package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ControllerEvidence;
import com.krdevops.springai.model.thymeleaf.ControllerMethodEvidence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** I-2C 완료 게이트: 골든 fixture Controller에서 매핑·모델·반환뷰·redirect 증거가 정확히 추출된다. */
class ControllerSourceReaderTest {

    private static final Path CONTROLLER =
            Path.of("src/test/resources/generation/baseline/crud-jsp/EgovEmployerController.java");

    private final ControllerSourceReader reader = new ControllerSourceReader();

    @Test
    void extractsAllMappedMethodsWithRoutesAndHttpMethods() throws IOException {
        ControllerEvidence evidence = read();

        assertThat(evidence.className()).isEqualTo("EgovEmployerController");
        assertThat(evidence.classLevelRequestMapping()).isNull();
        assertThat(evidence.methods()).extracting(ControllerMethodEvidence::route).containsExactlyInAnyOrder(
                "/emp/employerList.do", "/emp/employerDetail.do", "/emp/employerRegistView.do",
                "/emp/employerRegist.do", "/emp/employerUpdtView.do", "/emp/employerUpdt.do",
                "/emp/employerDelete.do");
    }

    @Test
    void listMethodBindsSearchVoAndAddsModelAttributes() throws IOException {
        ControllerMethodEvidence list = methodByRoute("/emp/employerList.do");

        assertThat(list.httpMethod()).isEqualTo("GET");
        assertThat(list.modelAttributeParamName()).isEqualTo("searchVO");
        assertThat(list.modelAttributeType()).isEqualTo("EmployerVO");
        assertThat(list.validated()).isFalse();
        assertThat(list.modelAttributesAdded()).containsExactlyInAnyOrder(
                "resultList", "paginationInfo", "currentMenuId", "currentPageSuffix", "menuContextUrl");
        assertThat(list.returnViewOrRedirect()).isEqualTo("employer/EgovEmployerList");
        assertThat(list.redirect()).isFalse();
    }

    @Test
    void insertMethodRequiresValidAndRedirectsOnSuccess() throws IOException {
        ControllerMethodEvidence insert = methodByRoute("/emp/employerRegist.do");

        assertThat(insert.httpMethod()).isEqualTo("POST");
        assertThat(insert.modelAttributeParamName()).isEqualTo("employerVO");
        assertThat(insert.validated()).isTrue();
        assertThat(insert.returnViewOrRedirect()).isEqualTo("redirect:/emp/employerList.do");
        assertThat(insert.redirect()).isTrue();
    }

    @Test
    void updateMethodTracesStringBuilderRedirectPrefix() throws IOException {
        ControllerMethodEvidence update = methodByRoute("/emp/employerUpdt.do");

        assertThat(update.returnViewOrRedirect()).isEqualTo("redirect:/emp/employerDetail.do?");
        assertThat(update.redirect()).isTrue();
    }

    @Test
    void deleteMethodTreatsUnannotatedVoParameterAsImplicitModelAttribute() throws IOException {
        ControllerMethodEvidence delete = methodByRoute("/emp/employerDelete.do");

        assertThat(delete.modelAttributeParamName()).isEqualTo("employerVO");
        assertThat(delete.modelAttributeType()).isEqualTo("EmployerVO");
    }

    private ControllerMethodEvidence methodByRoute(String route) throws IOException {
        List<ControllerMethodEvidence> matches = read().methods().stream()
                .filter(method -> method.route().equals(route)).toList();
        assertThat(matches).as("route " + route).hasSize(1);
        return matches.get(0);
    }

    private ControllerEvidence read() throws IOException {
        String content = Files.readString(CONTROLLER);
        return reader.read("EgovEmployerController.java", content);
    }
}
