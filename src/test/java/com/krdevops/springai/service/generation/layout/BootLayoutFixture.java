package com.krdevops.springai.service.generation.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 실제 {@link ThymeleafLayoutGenerationService} 로 Boot 프로젝트(application.yml 존재)에
 * layout 5종 + GNB 컴포넌트 4종 + {@code EgovWebMvcConfig.java} 를 생성하는 테스트 픽스처.
 * view 기술 종속이 없는 협력자(renderer/config/detector/writePort)는 실제 구현을 쓰고,
 * WAR 전용/부가 협력자만 목으로 대체한다.
 */
final class BootLayoutFixture {

    static final String PACKAGE_NAME = "egovframework.let.emp";

    private BootLayoutFixture() {}

    /** {@code projectRoot} 에 application.yml 을 만들어 Boot 로 인식시킨 뒤 layout/GNB/config 를 생성한다. */
    static LayoutGenerationResult generate(Path projectRoot) {
        writeBootMarker(projectRoot);

        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(projectRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        OperationHashFactory hashFactory = new OperationHashFactory(new ObjectMapper());
        FileSystemApprovedProjectWritePort writePort =
                new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory);

        ThymeleafLayoutValidator validator = Mockito.mock(ThymeleafLayoutValidator.class);
        lenient().when(validator.validateExisting(anyString(), anyString(), anyString()))
                .thenReturn(new ThymeleafLayoutValidator.LayoutValidationResult(
                        new ThymeleafLayoutValidator.LayoutReference("layout/default", "layout/breadcrumb", "layout"),
                        List.of()));

        ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer = Mockito.mock(ThymeleafRuntimeConfigurer.class);
        MyBatisRuntimeConfigurer myBatisRuntimeConfigurer = Mockito.mock(MyBatisRuntimeConfigurer.class);
        lenient().when(myBatisRuntimeConfigurer.ensureConfigured(anyString(), anyString()))
                .thenReturn(new MyBatisRuntimeConfigurer.ConfigurationResult(
                        true, false, true, projectRoot, "context-common.xml 없음 — 생략"));

        ClasspathAssetCopier assetCopier = Mockito.mock(ClasspathAssetCopier.class);
        lenient().when(assetCopier.copyLogo(any(), anyBoolean())).thenReturn("  (logo skipped in test)\n");

        ServletContextConfigurer servletContextConfigurer = Mockito.mock(ServletContextConfigurer.class);
        lenient().when(servletContextConfigurer.patch(any(), anyString()))
                .thenReturn(new ServletContextConfigurer.ServletContextPatchResult("unused", false));

        ThymeleafLayoutGenerationService service = new ThymeleafLayoutGenerationService(
                new CrudTemplateRenderer(crudFreemarkerConfiguration()),
                codeService,
                validator,
                thymeleafRuntimeConfigurer,
                myBatisRuntimeConfigurer,
                new ThymeleafLayoutGenerationPlanner(),
                new MainPageRenderer(),
                assetCopier,
                servletContextConfigurer,
                new ProjectTypeDetector(),
                new BootMvcConfigConfigurer(codeService, writePort, hashFactory),
                writePort);

        return service.generate(new GenerateThymeleafLayoutCommand(
                projectRoot, "layout", true, PACKAGE_NAME, "LETTNMENUINFO", "LETTNPROGRMLIST"));
    }

    private static void writeBootMarker(Path projectRoot) {
        try {
            Path yml = projectRoot.resolve("src/main/resources/application.yml");
            Files.createDirectories(yml.getParent());
            Files.writeString(yml, """
                    spring:
                      application:
                        name: emp
                    mybatis:
                      mapper-locations: classpath*:egovframework/mapper/**/*.xml
                    """, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Configuration crudFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                BootLayoutFixture.class.getClassLoader(), "templates/crud");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}
