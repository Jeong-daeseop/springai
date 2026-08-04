package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP6/ARCH-0610~0613, ARCH-0616~0618, ARCH-0621: BindingComposer가 실제 골든 fixture를
 * LIST/FORM/DETAIL Thymeleaf HTML로 렌더링하고, REVIEW_REQUIRED 계약은 렌더링을 BLOCK하는지
 * 검증한다.
 */
class BindingComposerTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final BindingContractAssembler assembler = new BindingContractAssembler(new GenerationIssueFactory());

    private BindingComposer composer;

    @BeforeEach
    void setUp() throws IOException {
        Configuration config = new Configuration(Configuration.VERSION_2_3_33);
        config.setDirectoryForTemplateLoading(new File(new File("").getAbsolutePath(), "src/main/resources/templates"));
        config.setDefaultEncoding("UTF-8");
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        composer = new BindingComposer(config, new GenerationIssueFactory());
    }

    @Test
    void listContractRendersSearchFormTableHeadersAndEachRowBinding() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 목록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("xmlns:th=\"http://www.thymeleaf.org\"");
        assertThat(html).contains("layout:decorate");
        assertThat(html).contains("직원 목록");
        assertThat(html).contains("th:action=\"@{/emp/employerList.do}\"");
        assertThat(html).contains("th:each=\"item : ${resultList}\"");
        for (String field : contract.displayFieldNames()) {
            assertThat(html).contains("th:text=\"${item." + field + "}\"");
        }
    }

    @Test
    void formContractRendersObjectActionCsrfAndFieldBindingsPerField() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.FORM);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 등록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("th:object=\"${employerVO}\"");
        assertThat(html).contains("th:action=\"@{/emp/employerRegist.do}\"");
        assertThat(html).contains("method=\"post\"");
        assertThat(html).contains("th:name=\"${_csrf.parameterName}\"");
        for (var field : contract.fields()) {
            assertThat(html).contains("th:field=\"*{" + field.fieldName() + "}\"");
        }
        assertThat(html).contains("th:errors=\"*{emplyrNm}\"");
    }

    @Test
    void detailContractRendersDisplayFieldsAndBackLink() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerDetail.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.DETAIL);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 상세", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("직원 상세");
        assertThat(html).contains("th:href=\"@{/emp/employerDetail.do}\"");
        for (String field : contract.displayFieldNames()) {
            assertThat(html).contains("th:text=\"${result." + field + "}\"");
        }
    }

    @Test
    void reviewRequiredContractBlocksComposeInsteadOfRenderingPartialHtml() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/sample/formSubmit.do", "POST", "formSubmit", "sampleVO", "SampleVO", true, false, List.of());
        ThymeleafBindingContract reviewRequired = new ThymeleafBindingContract(
                "sample-form", LegacyScreenRole.FORM, route, List.of(), List.of(), null,
                List.of(), List.of(), BindingContractStatus.REVIEW_REQUIRED, List.of(), null, Instant.now());

        ThymeleafGenerationStageResult<String> result = composer.compose(reviewRequired, "샘플", "layout/default");

        assertThat(result.successful()).isFalse();
        assertThat(result.value()).isNull();
        assertThat(result.hasFatalIssue()).isTrue();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("BINDING_REVIEW_REQUIRED_BLOCKS_COMPOSE"));
    }

    private ThymeleafBindingContract assembleFixture(
            String jspFile, String controllerFile, String voFile, LegacyScreenRole role) throws IOException {
        var jspEvidence = jspReader.read(jspFile, Files.readString(BASELINE.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                controllerFile, Files.readString(BASELINE.resolve(controllerFile)));
        var voEvidence = voReader.read(voFile, Files.readString(BASELINE.resolve(voFile)));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "emp-" + role.name().toLowerCase(Locale.ROOT), role,
                jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), List.of(), Instant.now());
        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);
        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        return result.value();
    }
}
