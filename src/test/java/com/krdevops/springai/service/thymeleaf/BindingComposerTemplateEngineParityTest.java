package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.BindingContractStatus;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafFieldBinding;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafRouteBinding;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * WP6/ARCH-0622: BindingComposer가 만든 문자열이 아니라, 실제 Spring {@link SpringTemplateEngine}로
 * 파싱·처리해서 route/field parity를 검증한다. 옛 {@code LegacyThymeleafRendererTest}(162bb3c에서
 * 삭제)는 FreeMarker 결과 문자열의 {@code .contains(...)}만 확인했고 실제 Thymeleaf 처리 테스트는
 * 존재한 적이 없었다 — ARCH-WP6 스코프 컷 메모의 "이번에 새로 추가" 항목.
 *
 * <p>{@code layout:decorate}는 실제 layout fragment 없이는 처리할 수 없으므로(별도 의존성인
 * thymeleaf-layout-dialect는 이 프로젝트에 없음), 이 테스트는 {@code <section
 * layout:fragment="content">...</section>} 안쪽만 잘라 처리한다 — ARCH-0622가 검증 대상으로 삼는
 * route/field parity(th:field/th:each/th:if/th:action/CSRF)는 전부 이 조각 안에 있다.
 */
class BindingComposerTemplateEngineParityTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");
    private static final Path BOARD_BASELINE = Path.of("src/test/resources/generation/baseline/board-jsp");

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final BindingContractAssembler assembler = new BindingContractAssembler(new GenerationIssueFactory());

    private BindingComposer composer;
    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() throws IOException {
        Configuration freemarkerConfig = new Configuration(Configuration.VERSION_2_3_33);
        freemarkerConfig.setDirectoryForTemplateLoading(
                new File(new File("").getAbsolutePath(), "src/main/resources/templates"));
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        composer = new BindingComposer(freemarkerConfig, new GenerationIssueFactory());

        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    void formHtml_rendersThFieldAndCsrfThroughRealTemplateEngineWithoutError() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/sample/formSubmit.do", "POST", "formSubmit", "sampleVO", "SampleVO", true, false, List.of());
        ThymeleafFieldBinding nameField = new ThymeleafFieldBinding(
                "name", "String", true, true, List.of("NotBlank"), "form:input", "CONTROLLER_VO", 1.0);
        ThymeleafFieldBinding emailField = new ThymeleafFieldBinding(
                "email", "String", true, false, List.of(), "form:input", "CONTROLLER_VO", 1.0);
        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                "sample-form", LegacyScreenRole.FORM, route, List.of(nameField, emailField),
                List.of(), null, List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());

        ThymeleafGenerationStageResult<String> composed = composer.compose(contract, "샘플 등록", "layout/default");
        assertThat(composed.successful()).as("issues: %s", composed.issues()).isTrue();
        String contentFragment = extractContentFragment(composed.value());

        SampleFormBean bean = new SampleFormBean();
        BindingResult bindingResult = new BeanPropertyBindingResult(bean, "sampleVO");
        bindingResult.addError(new FieldError("sampleVO", "name", "필수 입력 항목입니다."));

        Map<String, Object> model = new HashMap<>();
        model.put("sampleVO", bean);
        model.put(BindingResult.MODEL_KEY_PREFIX + "sampleVO", bindingResult);

        WebContext context = springAwareContext(model);
        context.setVariable("sampleVO", bean);
        context.setVariable("_csrf", new CsrfStub("X-CSRF-TOKEN", "_csrf", "csrf-token-value"));

        assertThatCode(() -> templateEngine.process(contentFragment, context)).doesNotThrowAnyException();
        String rendered = templateEngine.process(contentFragment, context);

        assertThat(rendered).contains("name=\"name\" value=\"\"");
        assertThat(rendered).contains("name=\"email\" value=\"\"");
        assertThat(rendered).contains("action=\"/sample/formSubmit.do\"");
        assertThat(rendered).contains("method=\"post\"");
        assertThat(rendered).contains("name=\"_csrf\" value=\"csrf-token-value\"").doesNotContain("th:name");
        assertThat(rendered).contains("필수 입력 항목입니다.");
    }

    @Test
    void listHtml_rendersThEachRowsThroughRealTemplateEngineWithoutError() throws IOException {
        ThymeleafBindingContract contract = assembleFixture(
                "EgovEmployerList.jsp", "EgovEmployerController.java", "EmployerVO.java", LegacyScreenRole.LIST);
        ThymeleafGenerationStageResult<String> composed = composer.compose(contract, "직원 목록", "layout/default");
        assertThat(composed.successful()).as("issues: %s", composed.issues()).isTrue();
        String contentFragment = extractContentFragment(composed.value());

        // th:text="${item.field}"는 SpEL 프로퍼티 접근이라 Map이 아닌 실제 getter를 가진 POJO가 필요하다.
        List<EmployerRow> rows = List.of(new EmployerRow("emp-1"), new EmployerRow("emp-2"));

        WebContext context = springAwareContext(Map.of());
        context.setVariable("resultList", rows);
        context.setVariable("message", null);
        context.setVariable("param", Map.of("searchKeyword", ""));

        String rendered = templateEngine.process(contentFragment, context);

        assertThat(rendered).isNotBlank();
        long rowCount = rendered.lines().filter(line -> line.contains("emp-1") || line.contains("emp-2")).count();
        assertThat(rowCount).isGreaterThanOrEqualTo(2);
        assertThat(rendered).contains("action=\"/emp/employerList.do\"");
    }

    @Test
    void detailHtml_rendersSecondaryListThroughRealTemplateEngineWithoutError() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/bbsMaster/bbsMasterDetail.do", "GET", "selectBbsMaster", null, null, false, false, List.of());
        ThymeleafFieldBinding bbsIdField = new ThymeleafFieldBinding(
                "bbsId", "String", true, false, List.of(), null, "VO_ONLY", 0.6);
        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                "bbs-master-detail", LegacyScreenRole.DETAIL, route, List.of(bbsIdField),
                List.of("bbsId"), "result", List.of("bbsId", "useAt"), "detailList",
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());

        ThymeleafGenerationStageResult<String> composed = composer.compose(contract, "BBSMASTER 상세", "layout/default");
        assertThat(composed.successful()).as("issues: %s", composed.issues()).isTrue();
        String contentFragment = extractContentFragment(composed.value());

        List<DetailRow> rows = List.of(new DetailRow("bbs-1", "Y"), new DetailRow("bbs-2", "N"));

        WebContext context = springAwareContext(Map.of());
        context.setVariable("result", new BbsMasterRow("BBSMASTER-1"));
        context.setVariable("detailList", rows);

        assertThatCode(() -> templateEngine.process(contentFragment, context)).doesNotThrowAnyException();
        String rendered = templateEngine.process(contentFragment, context);

        assertThat(rendered).contains("bbs-1").contains("bbs-2").contains("Y").contains("N");
    }

    /**
     * R6-T17: DETAIL 화면은 GET route를 쓰지만 기존 parity 테스트는 실제 렌더 결과에서
     * {@code method="get"}을 검증한 적이 없었다(POST만 확인). GET 화면에 method 속성이
     * "post"로 잘못 나오면 이 테스트가 잡아낸다.
     */
    @Test
    void detailHtml_rendersGetMethodAttributeThroughRealTemplateEngine() {
        ThymeleafRouteBinding route = new ThymeleafRouteBinding(
                "/bbsMaster/bbsMasterDetail.do", "GET", "selectBbsMaster", null, null, false, false, List.of());
        ThymeleafFieldBinding bbsIdField = new ThymeleafFieldBinding(
                "bbsId", "String", true, false, List.of(), null, "VO_ONLY", 0.6);
        ThymeleafBindingContract contract = new ThymeleafBindingContract(
                "bbs-master-detail-method", LegacyScreenRole.DETAIL, route, List.of(bbsIdField),
                List.of("bbsId"), "result", List.of("bbsId"), null,
                List.of(), List.of(), BindingContractStatus.RESOLVED, List.of(), null, Instant.now());

        ThymeleafGenerationStageResult<String> composed = composer.compose(contract, "BBSMASTER 상세", "layout/default");
        assertThat(composed.successful()).as("issues: %s", composed.issues()).isTrue();
        String contentFragment = extractContentFragment(composed.value());

        WebContext context = springAwareContext(Map.of());
        context.setVariable("result", new BbsMasterRow("BBSMASTER-1"));

        String rendered = templateEngine.process(contentFragment, context);

        assertThat(rendered).contains("href=\"/bbsMaster/bbsMasterDetail.do\"");
        assertThat(rendered).doesNotContain("method=\"post\"");
    }

    /**
     * R6-T17: 지금까지 parity 테스트는 crud-jsp/master-detail-jsp fixture만 써서 board fixture
     * 특유의 상속 SearchVO·searchVO model attribute 조합이 실제 Thymeleaf 엔진에서도 렌더되는지
     * 검증한 적이 없었다.
     */
    @Test
    void boardListHtml_rendersThEachRowsAndGetSearchFormThroughRealTemplateEngine() throws IOException {
        ThymeleafBindingContract contract = assembleBoardFixture(
                "EgovBbsList.jsp", "EgovBbsController.java", "BbsVO.java", "BbsSearchVO.java", LegacyScreenRole.LIST);
        assertThat(contract.route().httpMethod()).isEqualTo("GET");

        ThymeleafGenerationStageResult<String> composed = composer.compose(contract, "게시판 목록", "layout/default");
        assertThat(composed.successful()).as("issues: %s", composed.issues()).isTrue();
        String contentFragment = extractContentFragment(composed.value());

        List<BbsRow> rows = List.of(new BbsRow("bbs-1"), new BbsRow("bbs-2"));
        WebContext context = springAwareContext(Map.of());
        context.setVariable("resultList", rows);
        context.setVariable("message", null);
        context.setVariable("param", Map.of("searchKeyword", ""));

        String rendered = templateEngine.process(contentFragment, context);

        assertThat(rendered).isNotBlank();
        assertThat(rendered).doesNotContain("method=\"post\"");
        long rowCount = rendered.lines().filter(line -> line.contains("bbs-1") || line.contains("bbs-2")).count();
        assertThat(rowCount).isGreaterThanOrEqualTo(2);
        assertThat(rendered).contains("action=\"/bbs/bbsList.do\"");
    }

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
                new SourceRevisionRef("bbs-project", "rev-1", Instant.now()), List.of(), Instant.now());
        ThymeleafGenerationStageResult<ThymeleafBindingContract> result = assembler.assemble(analysis);
        assertThat(result.successful()).as("issues: %s", result.issues()).isTrue();
        return result.value();
    }

    /** {@code EgovBbsList.jsp} fixture의 displayFieldNames와 동일한 필드를 가진 게시판 목록 row. */
    public static class BbsRow {
        private final String noticeAt;
        private final String nttId;
        private final String nttSj;
        private final String ntcrNm;
        private final String frstRegistPnttm;
        private final String bbsId;

        BbsRow(String value) {
            this.noticeAt = value;
            this.nttId = value;
            this.nttSj = value;
            this.ntcrNm = value;
            this.frstRegistPnttm = value;
            this.bbsId = value;
        }

        public String getNoticeAt() {
            return noticeAt;
        }

        public String getNttId() {
            return nttId;
        }

        public String getNttSj() {
            return nttSj;
        }

        public String getNtcrNm() {
            return ntcrNm;
        }

        public String getFrstRegistPnttm() {
            return frstRegistPnttm;
        }

        public String getBbsId() {
            return bbsId;
        }
    }

    /** 옛 {@code LegacyThymeleafRenderer}가 만들던 layout:decorate 래핑을 제거하고 content fragment만 남긴다. */
    private String extractContentFragment(String html) {
        int start = html.indexOf("<section");
        int end = html.lastIndexOf("</section>") + "</section>".length();
        assertThat(start).as("content fragment 시작 태그를 찾지 못했습니다: %s", html).isGreaterThanOrEqualTo(0);
        return "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + html.substring(start, end) + "</html>";
    }

    private WebContext springAwareContext(Map<String, Object> model) {
        MockServletContext servletContext = new MockServletContext();
        StaticWebApplicationContext webApplicationContext = new StaticWebApplicationContext();
        webApplicationContext.setServletContext(servletContext);
        webApplicationContext.refresh();
        servletContext.setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContext requestContext = new RequestContext(request, response, servletContext, model);

        JakartaServletWebApplication webApplication = JakartaServletWebApplication.buildApplication(servletContext);
        IServletWebExchange webExchange = webApplication.buildExchange(request, response);

        SpringWebMvcThymeleafRequestContext thymeleafRequestContext =
                new SpringWebMvcThymeleafRequestContext(requestContext, request);

        WebContext context = new WebContext(webExchange, Locale.KOREA, model);
        context.setVariable(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT, thymeleafRequestContext);
        return context;
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

    public static class SampleFormBean {
        private String name;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public record CsrfStub(String headerName, String parameterName, String token) {
    }

    /** {@code EgovEmployerList.jsp} fixture의 displayFieldNames와 동일한 필드를 가진 목록 row. */
    public static class EmployerRow {
        private final String emplyrId;
        private final String emplyrNm;
        private final String emailAdres;
        private final String ofcpsNm;
        private final String frstRegistPnttm;
        private final String lastUpdtPnttm;

        EmployerRow(String value) {
            this.emplyrId = value;
            this.emplyrNm = value;
            this.emailAdres = value;
            this.ofcpsNm = value;
            this.frstRegistPnttm = value;
            this.lastUpdtPnttm = value;
        }

        public String getEmplyrId() {
            return emplyrId;
        }

        public String getEmplyrNm() {
            return emplyrNm;
        }

        public String getEmailAdres() {
            return emailAdres;
        }

        public String getOfcpsNm() {
            return ofcpsNm;
        }

        public String getFrstRegistPnttm() {
            return frstRegistPnttm;
        }

        public String getLastUpdtPnttm() {
            return lastUpdtPnttm;
        }
    }

    /** DETAIL 화면의 주 root(예: master-detail의 BBSMASTER) — {@code result.bbsId}가 참조하는 값. */
    public static class BbsMasterRow {
        private final String bbsId;

        BbsMasterRow(String bbsId) {
            this.bbsId = bbsId;
        }

        public String getBbsId() {
            return bbsId;
        }
    }

    /** DETAIL 화면의 부 root(예: master-detail의 BBSUSE 목록) — {@code item.bbsId}/{@code item.useAt}. */
    public static class DetailRow {
        private final String bbsId;
        private final String useAt;

        DetailRow(String bbsId, String useAt) {
            this.bbsId = bbsId;
            this.useAt = useAt;
        }

        public String getBbsId() {
            return bbsId;
        }

        public String getUseAt() {
            return useAt;
        }
    }
}
