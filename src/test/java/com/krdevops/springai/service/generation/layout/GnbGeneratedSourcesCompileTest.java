package com.krdevops.springai.service.generation.layout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * generateThymeleafLayout() 의 Boot 산출물(GNB VO/Mapper/Interceptor + EgovWebMvcConfig)이
 * 실제로 컴파일되는지 in-process javac + Lombok 으로 검증한다. DB/Docker 불필요.
 */
class GnbGeneratedSourcesCompileTest {

    @Test
    void generatedBootGnbSources_compileCleanly(@TempDir Path projectRoot) throws Exception {
        assumeTrue(GeneratedProjectCompiler.compilerAvailable(), "system Java compiler(JDK) 필요");

        LayoutGenerationResult result = BootLayoutFixture.generate(projectRoot);
        assertThat(result.projectType()).isEqualTo("BOOT");

        String pkgPath = BootLayoutFixture.PACKAGE_NAME.replace('.', '/');
        assertThat(projectRoot.resolve("src/main/java/" + pkgPath + "/cmm/vo/GnbMenuVO.java")).isRegularFile();
        assertThat(projectRoot.resolve("src/main/java/" + pkgPath + "/cmm/service/GnbMenuMapper.java")).isRegularFile();
        assertThat(projectRoot.resolve("src/main/java/" + pkgPath + "/cmm/web/EgovGnbMenuInterceptor.java")).isRegularFile();
        assertThat(projectRoot.resolve("src/main/java/" + pkgPath + "/config/EgovWebMvcConfig.java")).isRegularFile();
        assertThat(projectRoot.resolve("src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml")).isRegularFile();
        assertThat(projectRoot.resolve("src/main/java/" + pkgPath + "/main/web/MainController.java")).doesNotExist(); // generateThymeleafLayout은 MainController를 만들지 않음

        try (GeneratedProjectCompiler.Compiled compiled =
                     GeneratedProjectCompiler.compileJavaTree(projectRoot.resolve("src/main/java"))) {
            assertThat(compiled.errors())
                    .as("생성된 Boot GNB 소스 컴파일 에러:\n%s", String.join("\n", compiled.errors()))
                    .isEmpty();

            assertThat(compiled.classLoader().loadClass(BootLayoutFixture.PACKAGE_NAME + ".config.EgovWebMvcConfig"))
                    .isNotNull();
            Class<?> interceptor = compiled.classLoader()
                    .loadClass(BootLayoutFixture.PACKAGE_NAME + ".cmm.web.EgovGnbMenuInterceptor");
            Class<?> mapper = compiled.classLoader()
                    .loadClass(BootLayoutFixture.PACKAGE_NAME + ".cmm.service.GnbMenuMapper");
            // Lombok @RequiredArgsConstructor 로 (GnbMenuMapper) 생성자가 실제로 생성됐는지
            assertThat(interceptor.getConstructor(mapper)).isNotNull();
            Class<?> config = compiled.classLoader()
                    .loadClass(BootLayoutFixture.PACKAGE_NAME + ".config.EgovWebMvcConfig");
            assertThat(config.getConstructor(mapper)).isNotNull();
        }
    }

    @Test
    void generatedInterceptorXml_hasFullyQualifiedNamespaceAndResultMap(@TempDir Path projectRoot) throws Exception {
        BootLayoutFixture.generate(projectRoot);
        String xml = Files.readString(
                projectRoot.resolve("src/main/resources/egovframework/mapper/cmm/GnbMenuMapper.xml"));
        assertThat(xml)
                .contains("namespace=\"" + BootLayoutFixture.PACKAGE_NAME + ".cmm.service.GnbMenuMapper\"")
                .contains("type=\"" + BootLayoutFixture.PACKAGE_NAME + ".cmm.vo.GnbMenuVO\"")
                .contains("FROM LETTNMENUINFO m")
                .contains("LEFT JOIN LETTNPROGRMLIST p");
    }
}
