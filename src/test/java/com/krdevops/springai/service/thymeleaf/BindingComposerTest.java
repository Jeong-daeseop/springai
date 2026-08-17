package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /** R6-057: 회사 Design Token이 주어지면 참조된 CSS 변수 이름이 provenance 주석으로 남는다. */
    @Test
    void listContractWithDesignTokensEmitsProvenanceComment() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ResolvedDesignTokens tokens = new ResolvedDesignTokens(
                "krds", "2.2.2", null,
                Map.of("primary-60", "--krds-color-primary-60"),
                Map.of(),
                Map.of("sm", "--krds-spacing-sm"),
                Map.of(), Map.of(), Map.of(), List.of());

        ThymeleafGenerationStageResult<String> result = composer.compose(
                contract, "직원 목록", "layout/default", null, null, tokens);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("<!-- egov-design-token-provenance:");
        assertThat(html).contains("--krds-color-primary-60");
        assertThat(html).contains("--krds-spacing-sm");
    }

    /** Design Token이 없으면(null) provenance 주석 자체를 만들지 않는다. */
    @Test
    void listContractWithoutDesignTokensOmitsProvenanceComment() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 목록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).doesNotContain("egov-design-token-provenance");
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
    void listContractWithoutScreenSpecificationUsesStandardDensity() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 목록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).contains("egov-density-standard");
    }

    @Test
    void listContractAppliesApprovedScreenSpecificationLayoutDensity() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ScreenSpecification approved = screenSpecification(
                ScreenSpecStatus.APPROVED, LayoutDensity.COMPACT, FormColumnLayout.SINGLE_COLUMN);

        ThymeleafGenerationStageResult<String> result =
                composer.compose(contract, "직원 목록", "layout/default", approved);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).contains("egov-density-compact");
    }

    @Test
    void listContractIgnoresUnapprovedScreenSpecificationAndKeepsSafeDefault() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ScreenSpecification reviewRequired = screenSpecification(
                ScreenSpecStatus.REVIEW_REQUIRED, LayoutDensity.COMFORTABLE, FormColumnLayout.SINGLE_COLUMN);

        ThymeleafGenerationStageResult<String> result =
                composer.compose(contract, "직원 목록", "layout/default", reviewRequired);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).contains("egov-density-standard");
        assertThat(result.value()).doesNotContain("egov-density-comfortable");
    }

    @Test
    void formContractAppliesApprovedScreenSpecificationFormColumnLayout() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.FORM);
        ScreenSpecification approved = screenSpecification(
                ScreenSpecStatus.APPROVED, LayoutDensity.STANDARD, FormColumnLayout.TWO_COLUMN);

        ThymeleafGenerationStageResult<String> result =
                composer.compose(contract, "직원 등록", "layout/default", approved);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).contains("egov-layout-two-col");
    }

    @Test
    void listContractOmitsRegisterLinkWhenRegistRouteAbsentEvenIfApproved() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ScreenSpecification approved = screenSpecification(
                ScreenSpecStatus.APPROVED, LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN);

        ThymeleafGenerationStageResult<String> result =
                composer.compose(contract, "직원 목록", "layout/default", approved, null);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).doesNotContain("egov-btn-register");
    }

    @Test
    void listContractRendersRegisterLinkAtTopWhenRegistRouteProvidedWithDefaultPlacement() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<String> result = composer.compose(
                contract, "직원 목록", "layout/default", null, "/emp/employerRegist.do");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("th:href=\"@{/emp/employerRegist.do}\"");
        int registerIndex = html.indexOf("egov-btn-register");
        int tableIndex = html.indexOf("krds-table-wrap");
        assertThat(registerIndex).isGreaterThan(0);
        assertThat(registerIndex).isLessThan(tableIndex);
    }

    @Test
    void listContractRendersRegisterLinkAtBottomWhenApprovedActionPlacementIsBottomRight() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ScreenSpecification approved = screenSpecification(
                ScreenSpecStatus.APPROVED, ActionPlacement.BOTTOM_RIGHT);

        ThymeleafGenerationStageResult<String> result = composer.compose(
                contract, "직원 목록", "layout/default", approved, "/emp/employerRegist.do");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        int registerIndex = html.indexOf("egov-btn-register");
        int tableIndex = html.indexOf("krds-table-wrap");
        assertThat(registerIndex).isGreaterThan(tableIndex);
    }

    @Test
    void formContractWithoutScreenSpecificationStaysSingleColumn() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerRegist.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.FORM);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 등록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).doesNotContain("egov-layout-two-col");
    }

    @Test
    void listContractRendersAuthorityProvenanceCommentWhenSecurityEvidencePresent() throws IOException {
        ThymeleafBindingContract contract = withSecurityEvidence(assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST),
                List.of("@PreAuthorize(\"hasRole('ADMIN')\")"));

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 목록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).contains("egov-authority-provenance").contains("PreAuthorize");
    }

    @Test
    void listContractWithoutSecurityEvidenceOmitsAuthorityProvenanceComment() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 목록", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).doesNotContain("egov-authority-provenance");
    }

    private ThymeleafBindingContract withSecurityEvidence(
            ThymeleafBindingContract contract, List<String> securityEvidence) {
        ThymeleafRouteBinding original = contract.route();
        ThymeleafRouteBinding securedRoute = new ThymeleafRouteBinding(
                original.route(), original.httpMethod(), original.controllerMethodName(),
                original.modelAttributeName(), original.modelAttributeType(), original.validated(),
                original.redirect(), securityEvidence);
        return new ThymeleafBindingContract(
                contract.screenId(), contract.screenRole(), securedRoute, contract.fields(),
                contract.displayFieldNames(), contract.primaryDisplayAttributeName(),
                contract.modelAttributesResolved(), contract.modelAttributesUnresolved(),
                contract.status(), contract.issues(), contract.sourceRevision(), contract.createdAt());
    }

    @Test
    void detailContractRendersSecondaryListWhenSecondaryRootResolved() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/bbsMaster/bbsMasterDetail.do", "GET", "selectBbsMaster", null, null, false, false, List.of());
        ThymeleafFieldBinding bbsIdField = new ThymeleafFieldBinding(
                "bbsId", "String", true, false, List.of(), "c:out", "VO_ONLY", 0.6);
        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                "bbs-master-detail", LegacyScreenRole.DETAIL, route, List.of(bbsIdField),
                List.of("bbsId"), "result", List.of("bbsId", "useAt"), "detailList",
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "BBSMASTER 상세", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        String html = result.value();
        assertThat(html).contains("th:each=\"item : ${detailList}\"");
        assertThat(html).contains("th:text=\"${item.bbsId}\"");
        assertThat(html).contains("th:text=\"${item.useAt}\"");
    }

    @Test
    void detailContractOmitsSecondaryListWhenSecondaryRootAbsent() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerDetail.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.DETAIL);

        ThymeleafGenerationStageResult<String> result = composer.compose(contract, "직원 상세", "layout/default");

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        assertThat(result.value()).doesNotContain("th:each=\"item :");
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

    private ScreenSpecification screenSpecification(
            ScreenSpecStatus status, LayoutDensity layoutDensity, FormColumnLayout formColumnLayout) {
        return new ScreenSpecification(
                "spec-1", 1, status, "직원", "CRUD", "LIST", "ebt", "EMPLOYER",
                List.of(), List.of(), List.of(), layoutDensity, formColumnLayout, LocalDateTime.now());
    }

    private ScreenSpecification screenSpecification(ScreenSpecStatus status, ActionPlacement actionPlacement) {
        return new ScreenSpecification(
                "spec-1", 1, status, "직원", "CRUD", "LIST", "ebt", "EMPLOYER",
                List.of(), List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                actionPlacement, com.krdevops.springai.model.design.SearchPanelPlacement.ABOVE_TABLE,
                LocalDateTime.now());
    }
}
