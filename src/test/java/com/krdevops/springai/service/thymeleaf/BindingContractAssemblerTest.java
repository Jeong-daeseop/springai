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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WP6/ARCH-0601~0607, ARCH-0616~0618 완료 게이트 E2E: 골든 LIST·FORM·DETAIL fixture의
 * JSP·Controller·VO가 정상 연결되고, 충돌 정책의 FATAL/REVIEW_REQUIRED 판정이 동작한다.
 */
class BindingContractAssemblerTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");
    private static final Path MASTER_DETAIL_BASELINE =
            Path.of("src/test/resources/generation/baseline/master-detail-jsp");
    private static final Path BOARD_BASELINE = Path.of("src/test/resources/generation/baseline/board-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final BindingContractAssembler assembler =
            new BindingContractAssembler(new GenerationIssueFactory());

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
    void masterDetailScreenResolvesPrimaryAndSecondaryDisplayFieldsWhenSecondaryVoProvided() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.DETAIL,
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\"%>\n"
                        + "<div>${sampleVO.name}</div>\n"
                        + "<c:forEach items=\"${replyList}\" var=\"reply\">\n"
                        + "    <div>${reply.content}</div>\n"
                        + "</c:forEach>\n",
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));
        var secondaryVo = voReader.read("ReplyVO.java", vo("public class ReplyVO {\n"
                + "    private String content;\n"
                + "    public String getContent() { return content; }\n"
                + "    public void setContent(String content) { this.content = content; }\n"
                + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis, secondaryVo);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        ThymeleafBindingContract contract = result.value();
        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("sampleVO");
        assertThat(contract.displayFieldNames()).containsExactly("name");
        assertThat(contract.secondaryDisplayAttributeName()).isEqualTo("replyList");
        assertThat(contract.secondaryDisplayFieldNames()).containsExactly("content");
        assertThat(contract.issues()).noneMatch(issue -> issue.code().equals("SECONDARY_ROOT_FIELDS_UNVERIFIED"));
    }

    @Test
    void boardStyleDetailScreenWithoutSecondaryVoStaysReviewRequiredWithSpecificIssue() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.DETAIL,
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\"%>\n"
                        + "<div>${sampleVO.name}</div>\n"
                        + "<c:forEach items=\"${fileList}\" var=\"file\">\n"
                        + "    <div>${file.originalFileName}</div>\n"
                        + "</c:forEach>\n",
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        ThymeleafBindingContract contract = result.value();
        assertThat(contract.status()).isEqualTo(BindingContractStatus.REVIEW_REQUIRED);
        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("sampleVO");
        assertThat(contract.displayFieldNames()).containsExactly("name");
        assertThat(contract.secondaryDisplayAttributeName()).isNull();
        assertThat(contract.secondaryDisplayFieldNames()).isEmpty();
        assertThat(contract.issues()).anyMatch(issue -> issue.code().equals("SECONDARY_ROOT_FIELDS_UNVERIFIED"));
        assertThat(contract.issues()).noneMatch(issue -> issue.code().equals("MULTIPLE_DISPLAY_ROOTS_AMBIGUOUS"));
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

    @Test
    void displayFieldWithoutMatchingVoFieldIsReviewRequiredNotSilentlyDropped() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.DETAIL,
                "<div>${sampleVO.name}</div>\n"
                        + "<div>${sampleVO.attachmentUrl}</div>\n",
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().status()).isEqualTo(BindingContractStatus.REVIEW_REQUIRED);
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("DISPLAY_FIELD_WITHOUT_VO_FIELD"));
    }

    @Test
    void multipleDisplayRootsAreReviewRequiredNotSilentlyMisassigned() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.DETAIL,
                "<%@ taglib prefix=\"c\" uri=\"http://java.sun.com/jsp/jstl/core\"%>\n"
                        + "<div>${sampleVO.name}</div>\n"
                        + "<c:forEach items=\"${replyList}\" var=\"reply\">\n"
                        + "    <div>${reply.name}</div>\n"
                        + "</c:forEach>\n",
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().status()).isEqualTo(BindingContractStatus.REVIEW_REQUIRED);
        // WP6 3차 pass(2026-08-05): 이 모양(non-loop 주 root + loop 부 root)은 더 이상 "모호"하지
        // 않다 — 어느 쪽이 주/부 root인지는 명확하고, 관건은 부 root 필드 검증 여부뿐이라 더 구체적인
        // SECONDARY_ROOT_FIELDS_UNVERIFIED로 판정이 바뀌었다(REVIEW_REQUIRED로 막는 결론은 동일).
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("SECONDARY_ROOT_FIELDS_UNVERIFIED"));
    }

    @Test
    void masterDetailListScreenResolvesThroughExistingPipelineUnchanged() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                MASTER_DETAIL_BASELINE, "EgovBbsMasterList.jsp", "EgovBbsMasterController.java",
                "BbsMasterVO.java", LegacyScreenRole.LIST);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsMasterList.do");
        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("resultList");
        assertThat(contract.secondaryDisplayAttributeName()).isNull();
    }

    @Test
    void masterDetailGoldenFixtureDetailScreenResolvesWithSecondaryVo() throws IOException {
        var jspEvidence = jspReader.read("EgovBbsMasterDetail.jsp",
                Files.readString(MASTER_DETAIL_BASELINE.resolve("EgovBbsMasterDetail.jsp")));
        var controllerEvidence = controllerReader.read("EgovBbsMasterController.java",
                Files.readString(MASTER_DETAIL_BASELINE.resolve("EgovBbsMasterController.java")));
        var voEvidence = voReader.read("BbsMasterVO.java",
                Files.readString(MASTER_DETAIL_BASELINE.resolve("BbsMasterVO.java")));
        var secondaryVoEvidence = voReader.read("BbsuseVO.java",
                Files.readString(MASTER_DETAIL_BASELINE.resolve("BbsuseVO.java")));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "bbs-master-detail", LegacyScreenRole.DETAIL, jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("bbs-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result =
                assembler.assemble(analysis, secondaryVoEvidence);

        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        ThymeleafBindingContract contract = result.value();
        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsMasterDetail.do");
        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("result");
        assertThat(contract.displayFieldNames()).containsExactly("bbsId", "bbsNm", "bbsIntrcn");
        assertThat(contract.secondaryDisplayAttributeName()).isEqualTo("detailList");
        assertThat(contract.secondaryDisplayFieldNames())
                .containsExactly("bbsId", "useAt", "sendTargetClassify");
    }

    @Test
    void masterDetailFormScreenResolvesThroughExistingPipelineUnchanged() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                MASTER_DETAIL_BASELINE, "EgovBbsMasterRegist.jsp", "EgovBbsMasterController.java",
                "BbsMasterVO.java", LegacyScreenRole.FORM);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsMasterRegist.do");
        assertThat(contract.route().httpMethod()).isEqualTo("POST");
    }

    // ── ARCH-0619: BOARD 골든 fixture(BbsVO extends BbsSearchVO, 명시 매핑, CSRF 필터링) 완료 게이트 ──

    @Test
    void boardListScreenResolvesSearchFormFieldsInheritedFromSearchVoWithoutFatal() throws IOException {
        ThymeleafBindingContract contract = assembleBoardFixture(
                "EgovBbsList.jsp", "EgovBbsController.java", "BbsVO.java", "BbsSearchVO.java", LegacyScreenRole.LIST);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsList.do");
        assertThat(contract.route().httpMethod()).isEqualTo("GET");
        assertThat(contract.route().modelAttributeName()).isEqualTo("searchVO");

        // BbsSearchVO(상위 클래스)에만 선언된 필드 — VoSourceReader가 상속을 병합해야만 VO_FIELD를 찾는다.
        assertThat(contract.fields()).extracting("fieldName")
                .contains("pageIndex", "searchCondition", "searchKeyword", "bbsId");
        assertThat(contract.issues()).noneMatch(issue -> issue.severity().name().equals("FATAL"));

        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("resultList");
        assertThat(contract.displayFieldNames()).containsExactly(
                "noticeAt", "nttId", "nttSj", "ntcrNm", "frstRegistPnttm", "bbsId");
    }

    @Test
    void boardFormScreenResolvesPlainFormSubmitRouteAndRequiredValidation() throws IOException {
        ThymeleafBindingContract contract = assembleBoardFixture(
                "EgovBbsRegist.jsp", "EgovBbsController.java", "BbsVO.java", "BbsSearchVO.java",
                LegacyScreenRole.FORM);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsRegist.do");
        assertThat(contract.route().httpMethod()).isEqualTo("POST");
        assertThat(contract.route().modelAttributeName()).isEqualTo("bbsVO");
        assertThat(contract.route().validated()).isTrue();

        var nttSjBinding = contract.fields().stream()
                .filter(f -> f.fieldName().equals("nttSj")).findFirst().orElseThrow();
        assertThat(nttSjBinding.required()).isTrue();
        assertThat(nttSjBinding.boundJspTag()).isEqualTo("input");
        assertThat(contract.issues()).noneMatch(issue -> issue.severity().name().equals("FATAL"));
    }

    /**
     * DETAIL은 result(본문)와 file(첨부파일 loop) 두 root가 있는 실제 게시판 모양이라, secondary VO
     * 없이는 SECONDARY_ROOT_FIELDS_UNVERIFIED로 REVIEW_REQUIRED가 되는 게 맞는 판정이다 — 여기서
     * 확인할 것은 그 REVIEW_REQUIRED 판정 자체가 아니라, 삭제 폼(POST)이 더 이상
     * JSP_FORM_ACTION_NOT_BOUND FATAL을 내지 않는다는 것(Controller의 명시 매핑 수정 결과)과, CSRF
     * hidden input이 FORM_FIELD_WITHOUT_VO_FIELD FATAL을 내지 않는다는 것이다.
     */
    @Test
    void boardDetailScreenDeleteFormNoLongerFatalsOnExplicitMappingAndCsrfField() throws IOException {
        ThymeleafBindingContract contract = assembleBoardFixture(
                "EgovBbsDetail.jsp", "EgovBbsController.java", "BbsVO.java", "BbsSearchVO.java",
                LegacyScreenRole.DETAIL);

        assertThat(contract.status()).isEqualTo(BindingContractStatus.REVIEW_REQUIRED);
        assertThat(contract.route().route()).isEqualTo("/bbs/bbsDetail.do");
        assertThat(contract.route().httpMethod()).isEqualTo("GET");
        assertThat(contract.issues()).noneMatch(issue -> issue.severity().name().equals("FATAL"));
        assertThat(contract.issues()).anyMatch(issue -> issue.code().equals("SECONDARY_ROOT_FIELDS_UNVERIFIED"));

        assertThat(contract.primaryDisplayAttributeName()).isEqualTo("result");
        assertThat(contract.displayFieldNames()).containsExactly(
                "bbsId", "nttId", "nttSj", "nttCn", "ntcrNm", "noticeAt", "atchFileId", "frstRegistPnttm", "rdcnt");

        assertThat(contract.fields()).extracting("fieldName").contains("bbsId", "nttId");
    }

    @Test
    void controllerSecurityAnnotationAddsAuthorityReviewWarningWithoutBlockingContract() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.FORM,
                springForm("name"),
                securedController(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().status()).isEqualTo(BindingContractStatus.RESOLVED);
        assertThat(result.value().route().securityEvidence())
                .anyMatch(evidence -> evidence.contains("PreAuthorize"));
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("AUTHORITY_EVIDENCE_REQUIRES_REVIEW"));
    }

    @Test
    void controllerWithoutSecurityAnnotationAddsNoAuthorityWarning() {
        LegacyScreenAnalysis analysis = analysis(
                LegacyScreenRole.FORM,
                springForm("name"),
                controller(),
                vo("public class SampleVO {\n"
                        + "    private String name;\n"
                        + "    public String getName() { return name; }\n"
                        + "    public void setName(String name) { this.name = name; }\n"
                        + "}\n"));

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);

        assertThat(result.successful()).isTrue();
        assertThat(result.value().route().securityEvidence()).isEmpty();
        assertThat(result.issues()).noneMatch(issue -> issue.code().equals("AUTHORITY_EVIDENCE_REQUIRES_REVIEW"));
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

    private String securedController() {
        return "package test;\n"
                + "import org.springframework.stereotype.Controller;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.ModelAttribute;\n"
                + "import org.springframework.ui.ModelMap;\n"
                + "import org.springframework.security.access.prepost.PreAuthorize;\n"
                + "import jakarta.validation.Valid;\n"
                + "@Controller\n"
                + "public class SampleController {\n"
                + "    @GetMapping(\"/sample/formView.do\")\n"
                + "    public String formView(@ModelAttribute(\"sampleVO\") SampleVO sampleVO, ModelMap model) {\n"
                + "        model.addAttribute(\"sampleVO\", new SampleVO());\n"
                + "        return \"sample/SampleForm\";\n"
                + "    }\n"
                + "    @PreAuthorize(\"hasRole('ADMIN')\")\n"
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
        return assembleFixture(BASELINE, jspFile, controllerFile, voFile, role);
    }

    private ThymeleafBindingContract assembleFixture(
            Path baseline, String jspFile, String controllerFile, String voFile, LegacyScreenRole role)
            throws IOException {
        var jspEvidence = jspReader.read(jspFile, Files.readString(baseline.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                controllerFile, Files.readString(baseline.resolve(controllerFile)));
        var voEvidence = voReader.read(voFile, Files.readString(baseline.resolve(voFile)));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "emp-" + role.name().toLowerCase(Locale.ROOT), role,
                jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("emp-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);
        assertThat(result.successful())
                .as("issues: %s", result.issues())
                .isTrue();
        return result.value();
    }

    /** BOARD 계열 전용: {@code BbsVO extends BbsSearchVO} 상속 필드를 병합해 읽는다. */
    private ThymeleafBindingContract assembleBoardFixture(
            String jspFile, String controllerFile, String voFile, String superVoFile, LegacyScreenRole role)
            throws IOException {
        var jspEvidence = jspReader.read(jspFile, Files.readString(BOARD_BASELINE.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                controllerFile, Files.readString(BOARD_BASELINE.resolve(controllerFile)));
        var voEvidence = voReader.read(
                voFile, Files.readString(BOARD_BASELINE.resolve(voFile)),
                Files.readString(BOARD_BASELINE.resolve(superVoFile)));
        LegacyScreenAnalysis analysis = new LegacyScreenAnalysis(
                "bbs-" + role.name().toLowerCase(Locale.ROOT), role,
                jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("bbs-project", "rev-1", Instant.now()), java.util.List.of(), Instant.now());

        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);
        assertThat(result.successful())
                .as("issues: %s", result.issues())
                .isTrue();
        return result.value();
    }
}
