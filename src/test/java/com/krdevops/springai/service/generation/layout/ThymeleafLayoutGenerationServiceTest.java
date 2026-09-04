package com.krdevops.springai.service.generation.layout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.FileOutcome;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.Status;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WP7 2차 pass 잔여 항목/ARCH-0718: layout 5종/GNB 4종/main.html을 파일별로 즉시 저장하던 것을
 * 공용 {@code ApprovedProjectWritePort}로 배치 적용하도록 재구성한 뒤에도, 기존 동작(overwrite=false
 * 보존, 개별 저장 실패가 다른 파일에 영향 없이 계속 진행, 원래 파일별 outcome 순서 보존)이 그대로인지
 * 검증한다. 이 클래스는 이번 재구성 전까지 전용 테스트가 없었다 — 리팩터링 안전망으로 신규 작성.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThymeleafLayoutGenerationServiceTest {

    @Mock ThymeleafLayoutValidator thymeleafLayoutValidator;
    @Mock ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    @Mock MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    @Mock ClasspathAssetCopier classpathAssetCopier;
    @Mock ServletContextConfigurer servletContextConfigurer;

    private ThymeleafLayoutGenerationService service(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));

        when(servletContextConfigurer.patch(any(), anyString()))
                .thenReturn(new ServletContextConfigurer.ServletContextPatchResult("스킵", false));
        when(myBatisRuntimeConfigurer.ensureConfigured(anyString(), anyString()))
                .thenReturn(new MyBatisRuntimeConfigurer.ConfigurationResult(
                        true, false, true, outputRoot, "스킵"));
        when(thymeleafLayoutValidator.validateExisting(anyString(), anyString(), anyString()))
                .thenReturn(new ThymeleafLayoutValidator.LayoutValidationResult(
                        new ThymeleafLayoutValidator.LayoutReference("layout/default", "layout/breadcrumb", "layout"),
                        List.of()));
        when(classpathAssetCopier.copyLogo(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("  보존: logo\n");

        BootMvcConfigConfigurer bootMvcConfigConfigurer = new BootMvcConfigConfigurer(
                codeService, writePort, new OperationHashFactory(new ObjectMapper()));

        return new ThymeleafLayoutGenerationService(
                new CrudTemplateRenderer(crudFreemarkerConfiguration()), codeService, thymeleafLayoutValidator,
                thymeleafRuntimeConfigurer, myBatisRuntimeConfigurer, new ThymeleafLayoutGenerationPlanner(),
                new MainPageRenderer(), classpathAssetCopier, servletContextConfigurer,
                new ProjectTypeDetector(), bootMvcConfigConfigurer, writePort);
    }

    private static Configuration crudFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                ThymeleafLayoutGenerationServiceTest.class.getClassLoader(), "templates/crud");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }

    private GenerateThymeleafLayoutCommand command(Path outputRoot) {
        return command(outputRoot, false);
    }

    private GenerateThymeleafLayoutCommand command(Path outputRoot, boolean overwrite) {
        return new GenerateThymeleafLayoutCommand(
                outputRoot, "layout", overwrite, "egovframework.let.sample", "menu", "program");
    }

    @Test
    void generate_savesAllPlannedFilesWhenNoneExist(@TempDir Path outputRoot) {
        LayoutGenerationResult result = service(outputRoot).generate(command(outputRoot));

        assertThat(result.layoutFileOutcomes()).hasSize(5)
                .allMatch(outcome -> outcome.status() == Status.CREATED);
        assertThat(result.gnbComponentOutcomes()).hasSize(4)
                .allMatch(outcome -> outcome.status() == Status.CREATED);
        assertThat(result.mainHtmlOutcome().status()).isEqualTo(Status.CREATED);
        for (FileOutcome outcome : result.layoutFileOutcomes()) {
            assertThat(outcome.path()).isRegularFile();
        }
        assertThat(result.mainHtmlOutcome().path()).isRegularFile();
    }

    @Test
    void generate_preservesExistingFilesWithoutWritingWhenOverwriteFalse(@TempDir Path outputRoot) throws Exception {
        Path defaultHtml = outputRoot.resolve("src/main/resources/templates/layout/default.html");
        Files.createDirectories(defaultHtml.getParent());
        Files.writeString(defaultHtml, "hand-written-layout");

        LayoutGenerationResult result = service(outputRoot).generate(command(outputRoot));

        FileOutcome defaultOutcome = result.layoutFileOutcomes().stream()
                .filter(outcome -> outcome.path().equals(defaultHtml)).findFirst().orElseThrow();
        assertThat(defaultOutcome.status()).isEqualTo(Status.PRESERVED);
        assertThat(defaultHtml).hasContent("hand-written-layout");
        // 나머지 4개 layout 파일은 여전히 새로 생성된다.
        assertThat(result.layoutFileOutcomes()).filteredOn(outcome -> outcome.status() == Status.CREATED).hasSize(4);
    }

    @Test
    void generate_continuesPastIndividualWriteFailureAndReportsFailedOutcomeInOriginalOrder(
            @TempDir Path outputRoot) throws Exception {
        // layout 5종은 모두 같은 layout/ 디렉터리를 공유하므로(부모를 파일로 막으면 5개 전부 실패한다),
        // gnb.html "자리"만 디렉터리로 선점해 그 파일 하나만 쓰기 실패하게 한다 — 형제 파일들은
        // 같은 부모 디렉터리를 정상적으로 계속 쓸 수 있어야 한다(기존 파일별 독립 저장과 동일한 보장).
        // overwrite=true로 호출해야 Planner가 "이미 존재"로 skip하지 않고 실제로 쓰기를 시도한다.
        Path gnbHtml = outputRoot.resolve("src/main/resources/templates/layout/gnb.html");
        Files.createDirectories(gnbHtml);

        LayoutGenerationResult result = service(outputRoot).generate(command(outputRoot, true));

        List<FileOutcome> outcomes = result.layoutFileOutcomes();
        // Planner 순서: default, gnb, lnb, breadcrumb, footer — gnb만 실패해야 한다.
        assertThat(outcomes).extracting(FileOutcome::status)
                .containsExactly(Status.CREATED, Status.FAILED, Status.CREATED, Status.CREATED, Status.CREATED);
        FileOutcome gnbOutcome = outcomes.get(1);
        assertThat(gnbOutcome.path()).isEqualTo(gnbHtml);
        assertThat(gnbOutcome.detail()).isNotBlank();
        // 실패와 무관한 GNB 컴포넌트/main.html은 정상적으로 계속 생성된다.
        assertThat(result.gnbComponentOutcomes()).allMatch(outcome -> outcome.status() == Status.CREATED);
        assertThat(result.mainHtmlOutcome().status()).isEqualTo(Status.CREATED);
    }

    @Test
    void generate_onBootProject_registersInterceptorViaWebMvcConfigAndSkipsServletContext(
            @TempDir Path outputRoot) throws Exception {
        // application.yml 존재 → ProjectTypeDetector 가 BOOT 로 판정
        Path yml = outputRoot.resolve("src/main/resources/application.yml");
        Files.createDirectories(yml.getParent());
        Files.writeString(yml, "spring:\n");

        LayoutGenerationResult result = service(outputRoot).generate(
                new GenerateThymeleafLayoutCommand(
                        outputRoot, "layout", false, "egovframework.let.sample", "menu", "program"));

        assertThat(result.projectType()).isEqualTo("BOOT");
        assertThat(outputRoot.resolve(
                "src/main/java/egovframework/let/sample/config/EgovWebMvcConfig.java")).isRegularFile();
        assertThat(result.servletContextPatchMessage()).contains("WebMvcConfigurer", "EgovGnbMenuInterceptor");
        // WAR 전용 배선은 호출되지 않는다.
        verify(servletContextConfigurer, never()).patch(any(), anyString());
        verify(thymeleafRuntimeConfigurer, never())
                .ensureThymeleafRuntime(anyString(), anyString(), any());
        assertThat(result.runtimeSkipped()).isTrue();
    }

    @Test
    void generate_rejectsOutputPathOutsideAllowedLocations(
            @TempDir Path sharedParent, @TempDir Path elsewhere) {
        // basePath를 sharedParent 하위로 둬서 workspaceRoot(=basePath의 부모)가 sharedParent가
        // 되게 한다 — elsewhere는 별도 @TempDir라 sharedParent 아래에 있지 않으므로 확실히 범위 밖이다.
        Path allowedRoot = sharedParent.resolve("allowed-base");
        ThymeleafLayoutGenerationService service = service(allowedRoot);

        assertThatThrownBy(() -> service.generate(command(elsewhere)))
                .isInstanceOf(SecurityException.class);
        assertThat(elsewhere.resolve("src/main/resources/templates/layout/default.html")).doesNotExist();
    }
}
