package com.krdevops.springai.service.generation.layout;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.DockerClientFactory;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * generateThymeleafLayout() 의 Boot 산출물을 in-process 컴파일한 뒤, Testcontainers MySQL 에
 * 실제 LETTNMENUINFO/LETTNPROGRMLIST 를 만들어 두고:
 *  - GnbMenuMapper 가 실제 SQL 로 최상위 메뉴를 조회·매핑하는지 (Mapper XML resultMap 검증)
 *  - 생성된 EgovGnbMenuInterceptor.postHandle 이 그 결과로 gnbMenus 모델을 채우는지
 *  - 생성된 EgovWebMvcConfig 가 그 인터셉터를 InterceptorRegistry 에 등록하는지
 * 를 실제 부팅 경로로 검증한다.
 *
 * <p>이름이 *IntegrationTest 이므로 CI 빠른 세트에서는 제외된다. Docker 없으면 전체 skip.
 */
@Tag("testcontainers")
class BootGnbBootIntegrationTest {

    private static final String PKG = BootLayoutFixture.PACKAGE_NAME;
    private static final String MAPPER_FQN = PKG + ".cmm.service.GnbMenuMapper";

    private static MySQLContainer<?> mysql;

    @BeforeAll
    static void startContainer() {
        assumeTrue(GeneratedProjectCompiler.compilerAvailable(), "system Java compiler(JDK) 필요");
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 필요 (Testcontainers)");
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("ebt")
                .withInitScript("gnb/lettn-menu-schema.sql");
        mysql.start();
    }

    @AfterAll
    static void stopContainer() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void generatedGnbMapper_selectsTopLevelMenusFromRealMySql(@TempDir Path projectRoot) throws Exception {
        try (Ctx ctx = boot(projectRoot)) {
            try (SqlSession session = ctx.sqlSessionFactory.openSession()) {
                Object mapper = session.getMapper(ctx.mapperClass);
                @SuppressWarnings("unchecked")
                List<Object> rows = (List<Object>) ctx.selectGnbMenuList.invoke(mapper, 0L);

                assertThat(rows).hasSize(2);
                assertThat(menuNm(ctx, rows.get(0))).isEqualTo("직원관리");
                assertThat(url(ctx, rows.get(0))).isEqualTo("/emp/list.do");
                assertThat(menuNm(ctx, rows.get(1))).isEqualTo("게시판관리");
            }
        }
    }

    @Test
    void generatedInterceptor_populatesGnbMenusModel(@TempDir Path projectRoot) throws Exception {
        try (Ctx ctx = boot(projectRoot)) {
            try (SqlSession session = ctx.sqlSessionFactory.openSession()) {
                Object mapper = session.getMapper(ctx.mapperClass);
                Object interceptor = ctx.interceptorClass.getConstructor(ctx.mapperClass).newInstance(mapper);

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/emp/list.do");
                request.setServletPath("/emp/list.do");
                MockHttpServletResponse response = new MockHttpServletResponse();
                ModelAndView mav = new ModelAndView("egovframework/main/main");

                Method postHandle = ctx.interceptorClass.getMethod("postHandle",
                        HttpServletRequest.class, HttpServletResponse.class, Object.class, ModelAndView.class);
                postHandle.invoke(interceptor, request, response, new Object(), mav);

                assertThat(mav.getModel().get("gnbMenus")).asInstanceOf(list(Object.class)).hasSize(2);
                assertThat(mav.getModel().get("currentTopMenuNo")).isEqualTo(1000L);
            }
        }
    }

    @Test
    void generatedWebMvcConfig_registersGnbInterceptor(@TempDir Path projectRoot) throws Exception {
        try (Ctx ctx = boot(projectRoot)) {
            try (SqlSession session = ctx.sqlSessionFactory.openSession()) {
                Object mapper = session.getMapper(ctx.mapperClass);
                Class<?> configClass = ctx.classLoader.loadClass(PKG + ".config.EgovWebMvcConfig");
                Object config = configClass.getConstructor(ctx.mapperClass).newInstance(mapper);

                InterceptorRegistry registry = new InterceptorRegistry();
                configClass.getMethod("addInterceptors", InterceptorRegistry.class).invoke(config, registry);

                Method getInterceptors = InterceptorRegistry.class.getDeclaredMethod("getInterceptors");
                getInterceptors.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Object> registered = (List<Object>) getInterceptors.invoke(registry);

                assertThat(registered).anySatisfy(entry -> {
                    Object actual = unwrapMappedInterceptor(entry);
                    assertThat(actual.getClass().getName())
                            .isEqualTo(PKG + ".cmm.web.EgovGnbMenuInterceptor");
                });
            }
        }
    }

    // ── 공통 부트스트랩 ───────────────────────────────────────────────────────

    private Ctx boot(Path projectRoot) throws Exception {
        BootLayoutFixture.generate(projectRoot);

        GeneratedProjectCompiler.Compiled compiled =
                GeneratedProjectCompiler.compileJavaTree(projectRoot.resolve("src/main/java"));
        assertThat(compiled.errors())
                .as("생성 Boot 소스 컴파일 에러:\n%s", String.join("\n", compiled.errors()))
                .isEmpty();

        Class<?> voClass = compiled.classLoader().loadClass(PKG + ".cmm.vo.GnbMenuVO");
        Class<?> mapperClass = compiled.classLoader().loadClass(MAPPER_FQN);
        Class<?> interceptorClass = compiled.classLoader().loadClass(PKG + ".cmm.web.EgovGnbMenuInterceptor");

        UnpooledDataSource ds = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        org.apache.ibatis.session.Configuration mbCfg = new org.apache.ibatis.session.Configuration(
                new Environment("test", new JdbcTransactionFactory(), ds));

        Path xml = projectRoot.resolve("src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml");
        try (InputStream in = Files.newInputStream(xml)) {
            new XMLMapperBuilder(in, mbCfg, "cmm/GnbMenuMapper.xml", mbCfg.getSqlFragments()).parse();
        }
        // XML 을 이미 로드했음을 알려 addMapper 의 sibling XML 재파싱(중복 statement)을 막는다.
        mbCfg.addLoadedResource("namespace:" + MAPPER_FQN);
        mbCfg.addMapper(mapperClass);

        SqlSessionFactory sf = new SqlSessionFactoryBuilder().build(mbCfg);

        Ctx ctx = new Ctx();
        ctx.compiled = compiled;
        ctx.classLoader = compiled.classLoader();
        ctx.voClass = voClass;
        ctx.mapperClass = mapperClass;
        ctx.interceptorClass = interceptorClass;
        ctx.sqlSessionFactory = sf;
        ctx.selectGnbMenuList = mapperClass.getMethod("selectGnbMenuList", Long.class);
        ctx.getMenuNm = voClass.getMethod("getMenuNm");
        ctx.getUrl = voClass.getMethod("getUrl");
        return ctx;
    }

    private static Object unwrapMappedInterceptor(Object entry) {
        if (entry instanceof org.springframework.web.servlet.handler.MappedInterceptor mapped) {
            return mapped.getInterceptor();
        }
        return entry;
    }

    private static String menuNm(Ctx ctx, Object row) throws Exception {
        return (String) ctx.getMenuNm.invoke(row);
    }

    private static String url(Ctx ctx, Object row) throws Exception {
        return (String) ctx.getUrl.invoke(row);
    }

    private static final class Ctx implements AutoCloseable {
        GeneratedProjectCompiler.Compiled compiled;
        ClassLoader classLoader;
        Class<?> voClass;
        Class<?> mapperClass;
        Class<?> interceptorClass;
        SqlSessionFactory sqlSessionFactory;
        Method selectGnbMenuList;
        Method getMenuNm;
        Method getUrl;

        @Override
        public void close() {
            compiled.close();
        }
    }
}
