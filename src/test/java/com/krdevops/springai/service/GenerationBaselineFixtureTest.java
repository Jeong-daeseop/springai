package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.board.BoardLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailLayerDefinition;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.tools.ThymeleafLayoutTool;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD / 게시판 / Master-Detail / Thymeleaf layout 실제 렌더링 결과를 Golden File로 고정하는
 * Characterization 테스트(WP-0). WP-1~WP-4 리팩터링이 외부 생성 결과(Source 문자열)를 바꾸지
 * 않았는지 회귀 감지하는 기준선이다.
 *
 * <p>실제 MySQL(egov-mysql) 컨테이너에는 접속하지 않는다 — 기존 *OrchestrationServiceTest들과
 * 동일하게 고정된 fixture 컬럼 데이터(List&lt;Map&lt;String,Object&gt;&gt;)를 실제
 * {@code CrudModelFactory}/{@code CrudTemplateRenderer}(및 Board/MasterDetail 대응 클래스)에
 * 통과시켜 실제 렌더링된 Source 문자열을 만든다. 스키마 조회·DB 계층만 fixture로 대체하고
 * 렌더링 자체는 실제 프로덕션 클래스를 그대로 사용한다(Mock 없음).
 *
 * <p>FreeMarker {@link Configuration}은 {@code config/FreemarkerConfig}와 동일한 설정을
 * Spring 컨텍스트 없이 재구성한다 — 전체 Spring Boot 컨텍스트(DB/Redis/Ollama)를 띄우지 않고도
 * 실제 렌더러를 검증하기 위함이며, 설정값 자체는 FreemarkerConfig와 1:1로 동일하다.
 *
 * <p>템플릿의 {@code @since ${date}}는 {@code LocalDate.now()} 기반이라 매일 값이 바뀐다 —
 * 저장/비교 전에 오늘 날짜 문자열을 고정 placeholder("GENERATED_DATE")로 치환해 baseline이
 * 날짜 때문에 매일 깨지지 않게 한다(운영 코드는 건드리지 않고 테스트에서만 정규화).
 */
class GenerationBaselineFixtureTest {

    private static final Path BASELINE_ROOT =
            Path.of("src/test/resources/generation/baseline");
    private static final String DATE_PLACEHOLDER = "GENERATED_DATE";

    private static final String PACKAGE_NAME = "egovframework.let.emp";
    private static final String BOARD_PACKAGE_NAME = "egovframework.let.bbs";
    private static final String EGOV_VERSION = "5.0";

    private final Configuration crudFreemarkerConfig = crudFreemarkerConfiguration();
    private final Configuration boardFreemarkerConfig = boardFreemarkerConfiguration();
    private final CrudTemplateRenderer crudTemplateRenderer = new CrudTemplateRenderer(crudFreemarkerConfig);
    private final CrudModelFactory crudModelFactory = new CrudModelFactory();
    private final BoardModelFactory boardModelFactory = new BoardModelFactory();
    private final BoardTemplateRenderer boardTemplateRenderer = new BoardTemplateRenderer(boardFreemarkerConfig);
    private final MasterDetailTemplateRenderer masterDetailTemplateRenderer =
            new MasterDetailTemplateRenderer(boardFreemarkerConfig);

    // ─── CRUD ───────────────────────────────────────────────────────────────

    @Test
    void crudJspFixture() throws IOException {
        CrudTemplateModel model = crudModelFactory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", PACKAGE_NAME, EGOV_VERSION, employerColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.JSP, ScreenSubsetMode.LIST_ONLY, null);

        Map<String, String> files = new LinkedHashMap<>();
        for (CrudLayerDefinition layer : CrudLayerDefinition.JSP_LAYERS) {
            String code = crudTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
            String fileName = CrudLayerDefinition.resolveFileName(layer.layerKey(), "Employer", layer.fileNameSuffix());
            files.put(fileName, code);
        }
        verifyOrBootstrap("crud-jsp", files);
    }

    @Test
    void crudThymeleafReuseFixture() throws IOException {
        CrudTemplateModel model = crudModelFactory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", PACKAGE_NAME, EGOV_VERSION, employerColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF, ScreenSubsetMode.LIST_AND_DETAIL, null);

        Map<String, String> files = new LinkedHashMap<>();
        for (CrudLayerDefinition layer : CrudLayerDefinition.THYMELEAF_LAYERS) {
            if (CrudLayerDefinition.isLayoutLayer(layer.layerKey())) continue; // reuse는 layout 레이어를 저장하지 않음
            String code = crudTemplateRenderer.renderByLayerKey(
                    layer.layerKey(), model,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH,
                    CrudLayoutMode.REUSE);
            String fileName = CrudLayerDefinition.resolveFileName(layer.layerKey(), "Employer", layer.fileNameSuffix());
            files.put(fileName, code);
        }
        verifyOrBootstrap("crud-thymeleaf-reuse", files);
    }

    @Test
    void crudThymeleafCreateFixture() throws IOException {
        CrudTemplateModel model = crudModelFactory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", PACKAGE_NAME, EGOV_VERSION, employerColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF, ScreenSubsetMode.LIST_AND_DETAIL, null);

        Map<String, String> files = new LinkedHashMap<>();
        for (CrudLayerDefinition layer : CrudLayerDefinition.THYMELEAF_LAYERS) {
            String code = crudTemplateRenderer.renderByLayerKey(
                    layer.layerKey(), model,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH,
                    CrudLayoutMode.CREATE);
            String fileName = CrudLayerDefinition.resolveFileName(layer.layerKey(), "Employer", layer.fileNameSuffix());
            files.put(fileName, code);
        }
        verifyOrBootstrap("crud-thymeleaf-create", files);
    }

    // ─── 게시판(Board) ───────────────────────────────────────────────────────

    @Test
    void boardJspFixture() throws IOException {
        BoardTemplateModel model = boardModelFactory.fromSchemas(
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILEDETAIL",
                "Bbs", BOARD_PACKAGE_NAME, EGOV_VERSION, boardSchemas(),
                BoardProgramMetadata.fallback(null));

        Map<String, String> files = new LinkedHashMap<>();
        for (BoardLayerDefinition layer : BoardLayerDefinition.JSP_LAYERS) {
            String code = boardTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
            String fileName = BoardLayerDefinition.resolveFileName(layer.layerKey(), "Bbs", layer.fileNameSuffix());
            files.put(fileName, code);
        }
        verifyOrBootstrap("board-jsp", files);
    }

    @Test
    void boardThymeleafFixture() throws IOException {
        BoardTemplateModel model = boardModelFactory.fromSchemas(
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILEDETAIL",
                "Bbs", BOARD_PACKAGE_NAME, EGOV_VERSION, boardSchemas(),
                BoardProgramMetadata.fallback(null));

        Map<String, String> files = new LinkedHashMap<>();
        for (BoardLayerDefinition layer : BoardLayerDefinition.THYMELEAF_LAYERS) {
            String code = boardTemplateRenderer.renderByLayerKey(
                    layer.layerKey(), model,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH,
                    CrudLayoutMode.CREATE);
            String fileName = BoardLayerDefinition.resolveFileName(layer.layerKey(), "Bbs", layer.fileNameSuffix());
            files.put(fileName, code);
        }
        verifyOrBootstrap("board-thymeleaf", files);
    }

    // ─── Master-Detail ──────────────────────────────────────────────────────

    @Test
    void masterDetailJspFixture() throws IOException {
        MasterDetailTemplateModel model = masterDetailModel(CrudViewType.JSP);

        Map<String, String> files = new LinkedHashMap<>();
        for (MasterDetailLayerDefinition layer : MasterDetailLayerDefinition.JSP_LAYERS) {
            String code = masterDetailTemplateRenderer.renderByLayerKey(layer.layerKey(), model);
            String fileName = MasterDetailLayerDefinition.resolveFileName(layer, "BbsMaster", "Bbsuse");
            files.put(fileName, code);
        }
        verifyOrBootstrap("master-detail-jsp", files);
    }

    @Test
    void masterDetailThymeleafFixture() throws IOException {
        MasterDetailTemplateModel model = masterDetailModel(CrudViewType.THYMELEAF);

        Map<String, String> files = new LinkedHashMap<>();
        for (MasterDetailLayerDefinition layer : MasterDetailLayerDefinition.THYMELEAF_LAYERS) {
            String code = masterDetailTemplateRenderer.renderByLayerKey(
                    layer.layerKey(), model,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_BREADCRUMB_VIEW,
                    ThymeleafLayoutValidator.DEFAULT_LAYOUT_BASE_PATH,
                    CrudLayoutMode.CREATE);
            String fileName = MasterDetailLayerDefinition.resolveFileName(layer, "BbsMaster", "Bbsuse");
            files.put(fileName, code);
        }
        verifyOrBootstrap("master-detail-thymeleaf", files);
    }

    private MasterDetailTemplateModel masterDetailModel(CrudViewType viewType) {
        CrudTemplateModel masterModel = crudModelFactory.fromSchema(
                "LETTNBBSMASTER", "BbsMaster", BOARD_PACKAGE_NAME, EGOV_VERSION, masterDetailMasterColumns(),
                CrudProgramMetadata.fallback(null), viewType, ScreenSubsetMode.NONE, null);
        // "Bbsuse" == MasterDetailOrchestrationService.deriveDetailDomain("LETTNBBSUSE") 실제 결과와 동일
        CrudTemplateModel detailModel = crudModelFactory.fromSchema(
                "LETTNBBSUSE", "Bbsuse", BOARD_PACKAGE_NAME, EGOV_VERSION, masterDetailDetailColumns(),
                CrudProgramMetadata.fallback(null), viewType, ScreenSubsetMode.NONE, null);
        return new MasterDetailTemplateModel(masterModel, detailModel, "BBS_ID", "bbsId");
    }

    // ─── Thymeleaf layout(공통 layout 5종 + GNB 4종 + main.html) ──────────────

    @Test
    void thymeleafLayoutFixture() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("default.html", crudTemplateRenderer.renderLayoutByLayerKey(CrudLayerDefinition.LAYOUT_HTML, "layout"));
        files.put("gnb.html", crudTemplateRenderer.renderLayoutByLayerKey(CrudLayerDefinition.LAYOUT_GNB_HTML, "layout"));
        files.put("lnb.html", crudTemplateRenderer.renderLayoutByLayerKey(CrudLayerDefinition.LAYOUT_LNB_HTML, "layout"));
        files.put("breadcrumb.html", crudTemplateRenderer.renderLayoutByLayerKey(CrudLayerDefinition.LAYOUT_BREADCRUMB_HTML, "layout"));
        files.put("footer.html", crudTemplateRenderer.renderLayoutByLayerKey(CrudLayerDefinition.LAYOUT_FOOTER_HTML, "layout"));
        files.put("GnbMenuVO.java", crudTemplateRenderer.renderGnbMenuComponent(CrudLayerDefinition.LAYOUT_GNB_MENU_VO, PACKAGE_NAME));
        files.put("GnbMenuMapper.java", crudTemplateRenderer.renderGnbMenuComponent(CrudLayerDefinition.LAYOUT_GNB_MENU_MAPPER, PACKAGE_NAME));
        files.put("GnbMenuMapper.xml", crudTemplateRenderer.renderGnbMenuComponent(CrudLayerDefinition.LAYOUT_GNB_MENU_MAPPER_XML, PACKAGE_NAME));
        files.put("EgovGnbMenuInterceptor.java", crudTemplateRenderer.renderGnbMenuComponent(CrudLayerDefinition.LAYOUT_GNB_MENU_INTERCEPTOR, PACKAGE_NAME));
        files.put("main.html", invokeMainThymeleafHtml("layout/default", "layout/breadcrumb"));
        verifyOrBootstrap("layout", files);
    }

    /**
     * ThymeleafLayoutTool.mainThymeleafHtml(...)은 private static이라 리플렉션으로 호출한다.
     * 위 layout/GNB 9종은 ThymeleafLayoutTool.generateThymeleafLayout()이 내부적으로 호출하는
     * 것과 동일한 CrudTemplateRenderer 메서드를 그대로 사용하므로 바이트 단위로 동일한 결과이고,
     * main.html만 순수 텍스트 블록이라 별도로 리플렉션 호출한다(파일 I/O 없이 실제 로직만 실행).
     */
    private String invokeMainThymeleafHtml(String layoutView, String breadcrumbView) throws Exception {
        Method method = ThymeleafLayoutTool.class.getDeclaredMethod(
                "mainThymeleafHtml", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, layoutView, breadcrumbView);
    }

    // ─── Golden File 저장/비교 ──────────────────────────────────────────────

    private void verifyOrBootstrap(String fixtureGroup, Map<String, String> filesByRelativePath) throws IOException {
        Path dir = BASELINE_ROOT.resolve(fixtureGroup);
        Files.createDirectories(dir);

        List<String> sortedNames = new TreeMap<>(filesByRelativePath).keySet().stream().toList();
        for (String relativePath : sortedNames) {
            Path target = dir.resolve(relativePath);
            String normalized = normalizeGeneratedDate(filesByRelativePath.get(relativePath));
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, normalized, StandardCharsets.UTF_8);
            } else {
                String stored = Files.readString(target, StandardCharsets.UTF_8);
                assertThat(normalized)
                        .as("골든 fixture와 렌더링 결과가 달라졌습니다(회귀 감지): " + fixtureGroup + "/" + relativePath)
                        .isEqualTo(stored);
            }
        }

        Path fileList = dir.resolve("file-list.txt");
        String fileListContent = String.join("\n", sortedNames) + "\n";
        if (!Files.exists(fileList)) {
            Files.writeString(fileList, fileListContent, StandardCharsets.UTF_8);
        } else {
            assertThat(fileListContent)
                    .as("골든 fixture 파일 목록(파일 수·경로)이 달라졌습니다: " + fixtureGroup + "/file-list.txt")
                    .isEqualTo(Files.readString(fileList, StandardCharsets.UTF_8));
        }
    }

    private static String normalizeGeneratedDate(String content) {
        return content.replace(LocalDate.now().toString(), DATE_PLACEHOLDER);
    }

    // ─── FreeMarker 설정(config/FreemarkerConfig.java와 동일 설정) ────────────

    private static Configuration crudFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                GenerationBaselineFixtureTest.class.getClassLoader(), "templates/crud");
        applyCommonFreemarkerSettings(cfg);
        return cfg;
    }

    private static Configuration boardFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                GenerationBaselineFixtureTest.class.getClassLoader(), "templates");
        applyCommonFreemarkerSettings(cfg);
        return cfg;
    }

    private static void applyCommonFreemarkerSettings(Configuration cfg) {
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
    }

    // ─── Fixture 컬럼 데이터(민감정보/실제 DB 자격증명 없음) ──────────────────

    private static List<Map<String, Object>> employerColumns() {
        return List.of(
                column("EMPLYR_ID", "varchar", 20, false, true, "직원ID"),
                column("EMPLYR_NM", "varchar", 50, false, false, "직원명"),
                column("EMAIL_ADRES", "varchar", 100, true, false, "이메일주소"),
                column("OFCPS_NM", "varchar", 50, true, false, "직급명"),
                column("FRST_REGIST_PNTTM", "datetime", null, true, false, "최초등록시점"),
                column("LAST_UPDT_PNTTM", "datetime", null, true, false, "최종수정시점"));
    }

    private static Map<String, List<Map<String, Object>>> boardSchemas() {
        Map<String, List<Map<String, Object>>> schemas = new LinkedHashMap<>();
        schemas.put("main", List.of(
                column("BBS_ID", "varchar", 20, false, true, "게시판ID"),
                column("NTT_ID", "bigint", null, false, true, "게시글번호"),
                column("NTT_SJ", "varchar", 200, false, false, "제목"),
                column("NTT_CN", "varchar", 4000, true, false, "내용"),
                column("NTCR_NM", "varchar", 50, true, false, "작성자명"),
                column("NOTICE_AT", "varchar", 1, true, false, "공지여부"),
                column("ATCH_FILE_ID", "varchar", 20, true, false, "첨부파일ID"),
                column("FRST_REGIST_PNTTM", "datetime", null, true, false, "최초등록시점"),
                column("RDCNT", "int", null, true, false, "조회수")));
        schemas.put("use", List.of(column("BBS_ID", "varchar", 20, false, true, "게시판ID")));
        schemas.put("file", List.of(column("ATCH_FILE_ID", "varchar", 20, false, true, "첨부파일ID")));
        schemas.put("fileDetail", List.of(
                column("ATCH_FILE_ID", "varchar", 20, false, true, "첨부파일ID"),
                column("FILE_SN", "int", null, false, true, "파일일련번호")));
        return schemas;
    }

    private static List<Map<String, Object>> masterDetailMasterColumns() {
        return List.of(
                column("BBS_ID", "varchar", 20, false, true, "게시판ID"),
                column("BBS_NM", "varchar", 100, false, false, "게시판명"),
                column("BBS_INTRCN", "varchar", 2000, true, false, "게시판소개"));
    }

    private static List<Map<String, Object>> masterDetailDetailColumns() {
        return List.of(
                column("BBS_ID", "varchar", 20, false, true, "게시판ID"),
                column("USE_AT", "varchar", 1, false, false, "사용여부"),
                column("SEND_TARGET_CLASSIFY", "varchar", 20, true, false, "전송대상분류"));
    }

    private static Map<String, Object> column(
            String name, String dataType, Integer length, boolean nullable, boolean pk, String comment) {
        Map<String, Object> col = new LinkedHashMap<>();
        col.put("COLUMN_NAME", name);
        col.put("DATA_TYPE", dataType);
        col.put("CHARACTER_MAXIMUM_LENGTH", length == null ? null : (long) length);
        col.put("IS_NULLABLE", nullable ? "YES" : "NO");
        col.put("COLUMN_COMMENT", comment);
        col.put("COLUMN_KEY", pk ? "PRI" : "");
        return col;
    }
}
