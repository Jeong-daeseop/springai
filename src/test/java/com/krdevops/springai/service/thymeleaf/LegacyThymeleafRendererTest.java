package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BoundThymeleafView;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafSkeleton;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I-4C E2E: I-2 golden fixture(JSP+Controller+VO)를 실제로 끝까지(Reader→Assembler→Skeleton→
 * Composer→Renderer) 돌려 만든 HTML에 Binding Contract에서 나온 th:field/th:object/th:each/
 * th:action만 존재하는지, FreeMarker 잔여 구문이 새지 않는지 검증한다.
 *
 * <p>{@code Configuration}을 Spring Context 없이 직접 구성한다 — {@code FreemarkerConfig}의
 * {@code boardFreemarkerConfiguration}과 동일한 설정이며, 이 테스트는 실제 DB/Redis 기동 없이
 * 템플릿 로딩·렌더링 자체를 검증하는 것이 목적이라 무거운 {@code @SpringBootTest}가 필요 없다.
 */
class LegacyThymeleafRendererTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final LegacyBindingContractAssembler assembler =
            new LegacyBindingContractAssembler(new GenerationIssueFactory());
    private final ThymeleafSkeletonPlanner planner = new ThymeleafSkeletonPlanner();
    private final LegacyThymeleafViewComposer composer =
            new LegacyThymeleafViewComposer(new GenerationIssueFactory());
    private final LegacyThymeleafRenderer renderer = new LegacyThymeleafRenderer(freemarkerConfiguration());

    @Test
    void listScreenRendersSelfContainedSearchAndTableWithProvenanceOnly() throws IOException {
        String html = renderFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java",
                LegacyScreenRole.LIST, "직원 목록");

        assertThat(html).contains("th:action=\"@{/emp/employerList.do}\"");
        assertThat(html).contains("th:each=\"item : ${resultList}\"");
        assertThat(html).contains("th:text=\"${item.emplyrId}\"");
        assertThat(html).contains("th:text=\"${item.emplyrNm}\"");
        assertNoLeakedFreemarkerSyntax(html);
    }

    @Test
    void formScreenRendersThObjectAndThFieldOnlyForFieldsPresentInJsp() throws IOException {
        String html = renderFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java",
                LegacyScreenRole.FORM, "직원 등록");

        assertThat(html).contains("th:object=\"${employerVO}\"");
        assertThat(html).contains("th:action=\"@{/emp/employerRegist.do}\"");
        assertThat(html).contains("th:field=\"*{emplyrId}\"");
        assertThat(html).contains("th:field=\"*{emplyrNm}\"");
        assertThat(html).contains("th:field=\"*{emailAdres}\"");
        assertThat(html).contains("th:field=\"*{ofcpsNm}\"");
        // 검색/페이징 전용 VO 필드(searchCondition 등)는 이 폼의 JSP에 없었으므로 렌더되지 않는다.
        assertThat(html).doesNotContain("th:field=\"*{searchCondition}\"");
        assertNoLeakedFreemarkerSyntax(html);
    }

    @Test
    void detailScreenRendersDisplayFieldsFromPrimaryAttributeOnly() throws IOException {
        String html = renderFixture(
                "EgovEmployerDetail.jsp", "EgovEmployerController.java", "EmployerVO.java",
                LegacyScreenRole.DETAIL, "직원 상세");

        assertThat(html).contains("th:text=\"${result.emplyrId}\"");
        assertThat(html).contains("th:text=\"${result.emplyrNm}\"");
        assertThat(html).contains("th:href=\"@{/emp/employerDetail.do}\"");
        assertNoLeakedFreemarkerSyntax(html);
    }

    private void assertNoLeakedFreemarkerSyntax(String html) {
        assertThat(html).doesNotContain("<#").doesNotContain("[#").doesNotContain("</#");
    }

    private String renderFixture(
            String jspFile, String controllerFile, String voFile,
            LegacyScreenRole role, String pageTitle) throws IOException {
        var jspEvidence = jspReader.read(jspFile, Files.readString(BASELINE.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                controllerFile, Files.readString(BASELINE.resolve(controllerFile)));
        var voEvidence = voReader.read(voFile, Files.readString(BASELINE.resolve(voFile)));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "emp-" + role.name().toLowerCase(java.util.Locale.ROOT), role,
                jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> contractResult = assembler.assemble(analysis);
        assertThat(contractResult.successful()).as("issues: %s", contractResult.issues()).isTrue();
        ThymeleafBindingContract contract = contractResult.value();

        ThymeleafSkeleton skeleton = planner.plan(analysis.screenId(), role, pageTitle);
        ThymeleafGenerationStageResult<BoundThymeleafView> viewResult = composer.compose(skeleton, contract);
        assertThat(viewResult.successful()).as("issues: %s", viewResult.issues()).isTrue();

        return renderer.render(viewResult.value());
    }

    private static Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(LegacyThymeleafRendererTest.class.getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}
