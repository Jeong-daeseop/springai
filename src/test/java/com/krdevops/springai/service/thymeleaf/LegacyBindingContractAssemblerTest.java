package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-2 완료 게이트 E2E: 골든 LIST·FORM·DETAIL fixture의 JSP·Controller·VO가 정상 연결되고,
 * 충돌 정책의 FATAL/REVIEW_REQUIRED 판정이 문서(§I-2E)대로 동작한다.
 */
class LegacyBindingContractAssemblerTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final LegacyBindingContractAssembler assembler =
            new LegacyBindingContractAssembler(new GenerationIssueFactory());

    @Test
    void listScreenResolvesWithSelfSubmitSearchFormAndAllVoFieldsBound() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/emp/employerList.do");
        assertThat(contract.route().httpMethod()).isEqualTo("GET");
        assertThat(contract.route().modelAttributeName()).isEqualTo("searchVO");

        assertThat(contract.fields()).extracting("fieldName").contains(
                "pageIndex", "searchCondition", "searchKeyword", "emplyrId", "emplyrNm");
        assertThat(contract.fields()).filteredOn(f -> f.fieldName().equals("pageIndex"))
                .allMatch(f -> f.provenance().equals("CONTROLLER_VO") && f.writable());

        assertThat(contract.modelAttributesResolved()).contains("searchVO", "resultList", "paginationInfo", "message");
        assertThat(contract.issues()).noneMatch(issue -> issue.severity().name().equals("FATAL"));

        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("resultList");
        assertThat(contract.displayFieldNames()).containsExactly(
                "emplyrId", "frstRegistPnttm", "lastUpdtPnttm", "ofcpsNm", "emailAdres", "emplyrNm");
    }

    @Test
    void formScreenResolvesSubmitRouteAndRequiredValidation() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.FORM);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/emp/employerRegist.do");
        assertThat(contract.route().httpMethod()).isEqualTo("POST");
        assertThat(contract.route().modelAttributeName()).isEqualTo("employerVO");
        assertThat(contract.route().validated()).isTrue();

        var emplyrNmBinding = contract.fields().stream()
                .filter(f -> f.fieldName().equals("emplyrNm")).findFirst().orElseThrow();
        assertThat(emplyrNmBinding.required()).isTrue();
        assertThat(emplyrNmBinding.boundJspTag()).isEqualTo("form:input");
        assertThat(emplyrNmBinding.provenance()).isEqualTo("CONTROLLER_VO");
    }

    @Test
    void detailScreenResolvesDisplayOnlyRouteAndValidatesDeleteFormSeparately() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerDetail.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.DETAIL);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/emp/employerDetail.do");
        assertThat(contract.route().httpMethod()).isEqualTo("GET");

        assertThat(contract.fields()).filteredOn(f -> f.fieldName().equals("emplyrId"))
                .allMatch(f -> f.boundJspTag() != null && f.writable());
        assertThat(contract.issues()).noneMatch(issue -> issue.severity().name().equals("FATAL"));

        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("result");
        assertThat(contract.displayFieldNames()).containsExactly(
                "emplyrId", "emplyrNm", "emailAdres", "ofcpsNm", "frstRegistPnttm", "lastUpdtPnttm");
    }

    @Test
    void formFieldWithoutMatchingVoFieldIsFatalAndBlocksContract() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.FORM,
                springForm("name", "nonExistentField"),
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isFalse();
        assertThat(result.hasFatalIssue()).isTrue();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("FORM_FIELD_WITHOUT_VO_FIELD"));
    }

    @Test
    void writeBindingToReadOnlyVoFieldIsFatalAndBlocksContract() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.FORM,
                springForm("name", "readOnlyField"),
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    private String readOnlyField;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "    public String getReadOnlyField() { return readOnlyField; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("WRITE_BINDING_TO_READONLY_FIELD"));
    }

    @Test
    void formActionRouteMismatchIsFatalAndBlocksContract() {
        String jsp = "<%@ taglib prefix=\"form\" uri=\"http://www.springframework.org/tags/form\"%>\n"
                + "<form:form modelAttribute=\"sampleVO\" "
                + "action=\"${pageContext.request.contextPath}/sample/wrongRoute.do\" method=\"post\">\n"
                + "    <form:input path=\"name\"/>\n"
                + "</form:form>\n";
        LegacyScreenAnalysis analysis = analysis(LegacyScreenRole.FORM, jsp, controller(), vo(
                "public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("JSP_FORM_ACTION_NOT_BOUND"));
    }

    @Test
    void voWithNoFieldsIsReviewRequiredNotFatal() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.FORM,
                "<%@ taglib prefix=\"form\" uri=\"http://www.springframework.org/tags/form\"%>\n"
                        + "<form:form modelAttribute=\"sampleVO\" "
                        + "action=\"${pageContext.request.contextPath}/sample/formSubmit.do\" method=\"post\">\n"
                        + "</form:form>\n",
                controller(),
                vo("public class SampleVO {\n}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().status()).isEqualTo(BindingContractStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("VO_HAS_NO_FIELDS"));
    }

    private String springForm(String... paths) {
        StringBuilder builder = new StringBuilder();
        builder.append("<%@ taglib prefix=\"form\" uri=\"http://www.springframework.org/tags/form\"%>\n");
        builder.append("<form:form modelAttribute=\"sampleVO\" "
                + "action=\"${pageContext.request.contextPath}/sample/formSubmit.do\" method=\"post\">\n");
        for (String path : paths) {
            builder.append("    <form:input path=\"").append(path).append("\"/>\n");
        }
        builder.append("</form:form>\n");
        return builder.toString();
    }

    private String controller() {
        return "package test;\n"
                + "import org.springframework.stereotype.Controller;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.ModelAttribute;\n"
                + "import org.springframework.ui.ModelMap;\n"
                + "import jakarta.validation.Valid;\n"
                + "@Controller\n"
                + "public class SampleController {\n"
                + "    @GetMapping(\"/sample/formView.do\")\n"
                + "    public String formView(@ModelAttribute(\"sampleVO\") SampleVO sampleVO, ModelMap model) {\n"
                + "        model.addAttribute(\"sampleVO\", new SampleVO());\n"
                + "        return \"sample/SampleForm\";\n"
                + "    }\n"
                + "    @PostMapping(\"/sample/formSubmit.do\")\n"
                + "    public String formSubmit(@ModelAttribute(\"sampleVO\") @Valid SampleVO sampleVO, ModelMap model) {\n"
                + "        return \"redirect:/sample/list.do\";\n"
                + "    }\n"
                + "}\n";
    }

    private String vo(String body) {
        return "package test;\n" + body;
    }

    private LegacyScreenAnalysis analysis(
            LegacyScreenRole role, String jspContent, String controllerContent, String voContent) {
        var jspEvidence = jspReader.read("SampleForm.jsp", jspContent);
        var controllerEvidence = controllerReader.read("SampleController.java", controllerContent);
        var voEvidence = voReader.read("SampleVO.java", voContent);
        return new LegacyScreenAnalysis(
                "sample-form", role, jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("sample-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());
    }

    private ThymeleafBindingContract assembleFixture(
            String jspFile, String controllerFile, String voFile, LegacyScreenRole role) throws IOException {
        var jspEvidence = jspReader.read(jspFile, Files.readString(BASELINE.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                controllerFile, Files.readString(BASELINE.resolve(controllerFile)));
        var voEvidence = voReader.read(voFile, Files.readString(BASELINE.resolve(voFile)));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "emp-" + role.name().toLowerCase(java.util.Locale.ROOT), role,
                jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);
        assertThat(result.successful())
                .as("issues: %s", result.issues())
                .isTrue();
        return result.value();
    }
}
