package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
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
 * WP8/ARCH-0802/0805: WP6 파이프라인(Assembler→Composer)의 실제 golden fixture 산출물이 WP8
 * Validation Gate(문자열 기반 묶음)를 통과하는지 증명한다 — 두 WP가 서로 남남으로 남지 않고 실제로
 * 이어져 있음을 보여주는 테스트다.
 */
class ThymeleafValidationGatePipelineTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final BindingContractAssembler assembler = new BindingContractAssembler(new GenerationIssueFactory());

    private BindingComposer composer;
    private ValidationGateExecutor gateExecutor;

    @BeforeEach
    void setUp() throws IOException {
        Configuration config = new Configuration(Configuration.VERSION_2_3_33);
        config.setDirectoryForTemplateLoading(new File(new File("").getAbsolutePath(), "src/main/resources/templates"));
        config.setDefaultEncoding("UTF-8");
        config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        composer = new BindingComposer(config, new GenerationIssueFactory());
        gateExecutor = new ValidationGateExecutor();
    }

    @Test
    void listGoldenFixturePassesGatesWithoutBlocking() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        String html = composer.compose(contract, "직원 목록", "layout/default").value();

        ValidationReport report = gateExecutor.runThymeleafGates(contract, html);

        assertThat(report.blocked()).as("issues: %s", report.results()).isFalse();
        assertThat(report.inputHash()).isNotBlank();
        assertThat(report.results()).extracting("gateType")
                .contains(ValidationGateType.TEMPLATE_ENGINE_RENDER);
    }

    @Test
    void formGoldenFixturePassesGatesWithoutBlocking() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.FORM);
        String html = composer.compose(contract, "직원 등록", "layout/default").value();

        ValidationReport report = gateExecutor.runThymeleafGates(contract, html);

        assertThat(report.blocked()).as("issues: %s", report.results()).isFalse();
    }

    @Test
    void detailContractPassesAutomaticRenderBindingAndRouteGates() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/bbsMaster/bbsMasterList.do", "GET", "selectBbsMaster", null, null,
                false, false, List.of());
        ThymeleafFieldBinding bbsId = new ThymeleafFieldBinding(
                "bbsId", "String", true, false, List.of(), null, "VO_ONLY", 0.6);
        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                "bbs-master-detail", LegacyScreenRole.DETAIL, route, List.of(bbsId),
                List.of("bbsId"), "result", List.of("bbsId", "useAt"), "detailList",
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());
        String html = composer.compose(contract, "BBSMASTER 상세", "layout/default").value();

        ValidationReport report = gateExecutor.runThymeleafGates(contract, html);

        assertThat(report.blocked()).as("issues: %s", report.results()).isFalse();
        assertThat(report.results()).allSatisfy(result -> assertThat(result.passed()).isTrue());
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
