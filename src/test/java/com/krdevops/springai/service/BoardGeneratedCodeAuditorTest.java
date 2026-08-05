package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.model.board.BoardDisplayModel;
import com.krdevops.springai.model.board.BoardRouteModel;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoardGeneratedCodeAuditorTest {

    // 이 테스트는 MyBatisRuntimeConfigurer의 순수 패키지 병합 로직만 쓰고 ensureConfigured(파일
    // write)는 호출하지 않으므로, write 관련 협력자는 어떤 값이든 상관없다.
    private final BoardGeneratedCodeAuditor auditor = new BoardGeneratedCodeAuditor(new MyBatisRuntimeConfigurer(
            new CodeService(new EgovProperties()),
            new FileSystemApprovedProjectWritePort(new SafePathResolver(), new OperationHashFactory(new ObjectMapper())),
            new OperationHashFactory(new ObjectMapper())));

    @Test
    void auditsGeneratedBoardContracts(@TempDir Path root) throws Exception {
        writeValidThymeleafProject(root);

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 통과")
                .contains("CSS·CSRF");
    }

    @Test
    void failsWhenKrdsComponentHasNoSizeModifier(@TempDir Path root) throws Exception {
        ProjectFiles files = writeValidThymeleafProject(root);
        Files.writeString(files.list(),
                "<section class=\"egov-crud-page\">bbsId <input class=\"krds-input\"></section>");

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 실패")
                .contains("krds-input 크기 modifier 누락");
    }

    @Test
    void failsWhenPostFormHasNoCsrfContract(@TempDir Path root) throws Exception {
        ProjectFiles files = writeValidThymeleafProject(root);
        Files.writeString(files.regist(),
                "<section class=\"egov-crud-page\">bbsId <form method=\"post\"></form></section>");

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 실패")
                .contains("Regist 화면 CSRF 조건 누락")
                .contains("Regist 화면 CSRF 토큰 누락");
    }

    @Test
    void failsWhenTextareaTokenOverrideIsMissing(@TempDir Path root) throws Exception {
        ProjectFiles files = writeValidThymeleafProject(root);
        String css = Files.readString(files.css())
                .replace("--krds-input--textarea-size-height: 220px;", "");
        Files.writeString(files.css(), css);

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 실패")
                .contains("--krds-input--textarea-size-height");
    }

    @Test
    void failsWhenGeneratedNttIdIsValidatedAsUserInput(@TempDir Path root) throws Exception {
        ProjectFiles files = writeValidThymeleafProject(root);
        Files.writeString(files.vo(), "@NotNull\nprivate Long nttId;");

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 실패")
                .contains("VO 생성형 nttId에 입력 필수 검증");
    }

    @Test
    void failsWhenMapperPackageIsOutsideScannerRange(@TempDir Path root) throws Exception {
        writeValidThymeleafProject(root);
        Path contextCommon = root.resolve(MyBatisRuntimeConfigurer.CONTEXT_COMMON_XML);
        Files.writeString(contextCommon, Files.readString(contextCommon)
                .replace("value=\"egovframework.let\"", "value=\"egovframework.let.com\""));

        assertThat(auditor.audit(root.toString(), model(), CrudViewType.THYMELEAF, "layout"))
                .contains("감사 실패")
                .contains("MyBatis Mapper 런타임 설정 누락")
                .contains("egovframework.let.cop.bbs.service.impl");
    }

    private ProjectFiles writeValidThymeleafProject(Path root) throws Exception {
        BoardTemplateModel model = model();
        Path controller = root.resolve("src/main/java/egovframework/let/cop/bbs/web/EgovInfoNoticeController.java");
        Path mapper = root.resolve("src/main/resources/egovframework/mapper/infoNotice/InfoNoticeMapper.xml");
        Path templates = root.resolve("src/main/resources/templates");
        Files.createDirectories(controller.getParent());
        Files.createDirectories(mapper.getParent());
        Files.writeString(controller, "hasCompositeKey resolveBbsId \"/cop/bbs/selectBoardList.do\"");
        Files.writeString(mapper, "WHERE BBS_ID = #{bbsId} AND NTT_ID = #{nttId} "
                + "selectNextInfoNoticeNttId FOR UPDATE");

        Path vo = root.resolve("src/main/java/egovframework/let/cop/bbs/service/InfoNoticeVO.java");
        Path serviceImpl = root.resolve(
                "src/main/java/egovframework/let/cop/bbs/service/impl/EgovInfoNoticeServiceImpl.java");
        Files.createDirectories(vo.getParent());
        Files.createDirectories(serviceImpl.getParent());
        Files.writeString(vo, "private Long nttId;");
        Files.writeString(serviceImpl,
                "vo.setNttId(infoNoticeMapper.selectNextInfoNoticeNttId());");

        Path viewRoot = templates.resolve("infoNotice");
        Files.createDirectories(viewRoot);
        Path list = viewRoot.resolve("EgovInfoNoticeList.html");
        Path detail = viewRoot.resolve("EgovInfoNoticeDetail.html");
        Path regist = viewRoot.resolve("EgovInfoNoticeRegist.html");
        Path updt = viewRoot.resolve("EgovInfoNoticeUpdt.html");
        Files.writeString(list, validView(false));
        Files.writeString(detail, validView(true));
        Files.writeString(regist, validView(true));
        Files.writeString(updt, validView(true));

        Path layout = templates.resolve("layout");
        Files.createDirectories(layout);
        for (String file : List.of("default.html", "gnb.html", "lnb.html", "breadcrumb.html", "footer.html")) {
            Files.writeString(layout.resolve(file), "ok");
        }

        Path servletContext = root.resolve(
                "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(servletContext.getParent());
        Files.writeString(servletContext,
                "<context:component-scan base-package=\"egovframework.let.cop.bbs.web\"/>");

        Path contextCommon = root.resolve(
                "src/main/resources/egovframework/spring/context-common.xml");
        Files.createDirectories(contextCommon.getParent());
        Files.writeString(contextCommon, """
                <beans>
                    <bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                        <property name="mapperLocations" value="classpath*:egovframework/mapper/**/*.xml"/>
                    </bean>
                    <bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
                        <property name="basePackage" value="egovframework.let"/>
                    </bean>
                </beans>
                """);

        Path css = root.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, """
/* === egov-board-crud:start === */
:root { --egov-screen-control-height: 38px; --egov-screen-textarea-min-height: 220px; --egov-screen-link-font-size: 13px; }
.krds-btn { --krds-button--size-height-medium: 38px; }
.krds-input { --krds-input--size-height-medium: 38px; --krds-input--textarea-size-height: 220px; }
.krds-form-select { --krds-form-select--size-height-medium: 38px; }
.krds-table-wrap .tbl.data { --krds-table--data-tbody-padding: 10px; }
.krds-pagination { display: flex; align-items: center; }
.egov-primary-text, .egov-detail-link, .egov-file-detail-link, .egov-file-empty, .egov-post-nav-link { font-size: var(--egov-screen-link-font-size); }
/* === egov-board-crud:end === */
""");
        return new ProjectFiles(list, detail, regist, updt, css, vo);
    }

    private String validView(boolean csrf) {
        String csrfInput = csrf
                ? "<input th:if=\"${_csrf != null}\" th:name=\"${_csrf.parameterName}\" th:value=\"${_csrf.token}\">"
                : "";
        return "<section class=\"egov-crud-page\">bbsId "
                + "<input class=\"krds-input medium\"><button class=\"krds-btn medium\"></button>"
                + csrfInput + "</section>";
    }

    private BoardTemplateModel model() {
        FieldModel bbsId = new FieldModel("BBS_ID", "bbsId", "String", "게시판", true,
                true, true, 20, "VARCHAR");
        FieldModel nttId = new FieldModel("NTT_ID", "nttId", "Long", "게시물", true,
                true, false, null, "BIGINT");
        return new BoardTemplateModel("egovframework.let.cop.bbs", "InfoNotice", "infoNotice", "LETTNBBS",
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "/cop/bbs/infoNotice",
                "2026-07-15", "5.0", true, bbsId, nttId, false, null, null,
                List.of(bbsId, nttId), List.of(), List.of(), List.of(), List.of(), false,
                new BoardDisplayModel("EgovInfoNotice", "공지사항", "알림정보"),
                new BoardRouteModel("/cop/bbs/infoNotice", null,
                        "/cop/bbs/selectBoardList.do", "BBS_NOTICE"));
    }

    private record ProjectFiles(Path list, Path detail, Path regist, Path updt, Path css, Path vo) {}
}
