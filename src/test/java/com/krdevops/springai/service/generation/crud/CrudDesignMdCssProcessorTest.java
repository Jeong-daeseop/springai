package com.krdevops.springai.service.generation.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.thymeleaf.AppliedDesignRules;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.KrdsStylesConfigurer;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver;
import com.krdevops.springai.service.thymeleaf.DesignMdRuleLoader;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CRUD auto 경로에 DESIGN.md 기준 KRDS 토큰을 반영하는 {@link CrudDesignMdCssProcessor} 검증.
 * designSystemProfileId가 없으면 조용히 건너뛰고(supports=false), 있으면 실제 styles.css를
 * patch하며, 어느 단계든 실패해도 non-fatal(항상 ok())임을 확인한다.
 */
class CrudDesignMdCssProcessorTest {

    @TempDir
    Path projectRoot;

    private final DesignMdRuleLoader designMdRuleLoader = mock(DesignMdRuleLoader.class);
    private final CompanyDesignTokenResolver companyDesignTokenResolver = mock(CompanyDesignTokenResolver.class);
    private KrdsStylesConfigurer krdsStylesConfigurer;
    private CrudDesignMdCssProcessor sut;

    private void setUpConfigurer() {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(projectRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        krdsStylesConfigurer = new KrdsStylesConfigurer(
                codeService, writePort, new OperationHashFactory(new ObjectMapper()));
        sut = new CrudDesignMdCssProcessor(designMdRuleLoader, companyDesignTokenResolver, krdsStylesConfigurer);
    }

    @Test
    void supports_falseWhenNoDesignSystemProfileIdAttribute() {
        setUpConfigurer();
        assertThat(sut.supports(context(Map.of()))).isFalse();
    }

    @Test
    void supports_trueWhenDesignSystemProfileIdPresent() {
        setUpConfigurer();
        assertThat(sut.supports(context(Map.of(
                CrudGenerationAttributes.DESIGN_SYSTEM_PROFILE_ID, "profile-1")))).isTrue();
    }

    @Test
    void process_patchesStylesCssWhenDesignMdAndTokensResolveSuccessfully() throws Exception {
        setUpConfigurer();
        Path css = projectRoot.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user { color: black; }\n");

        AppliedDesignRules rules = new AppliedDesignRules(
                projectRoot.resolve("DESIGN.md").toString(), "hash", "1.0", List.of(), List.of(), List.of());
        when(designMdRuleLoader.load(eq(projectRoot.toString())))
                .thenReturn(ThymeleafGenerationStageResult.success(rules, List.of()));
        ResolvedDesignTokens tokens = new ResolvedDesignTokens(
                "profile-1", "1", null,
                Map.of("primary", "--krds-color-light-primary-60"),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        when(companyDesignTokenResolver.resolve(eq("profile-1"), eq(rules)))
                .thenReturn(ThymeleafGenerationStageResult.success(tokens, List.of()));

        var result = sut.process(processingContext(css, "profile-1"));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(css))
                .contains(KrdsStylesConfigurer.DESIGN_MD_TOKEN_START_MARKER)
                .contains("--design-md-primary: var(--krds-color-light-primary-60);");
    }

    @Test
    void process_designMdLoadFails_returnsOkWithoutTouchingCss() throws Exception {
        setUpConfigurer();
        Path css = projectRoot.resolve("src/main/webapp/resources/css/styles.css");
        Files.createDirectories(css.getParent());
        Files.writeString(css, ".user { color: black; }\n");
        String before = Files.readString(css);

        GenerationIssue fatal = new GenerationIssue(
                "DESIGN_MD_PARSE_FAILED", GenerationIssue.Severity.FATAL, "R6-055",
                null, "DESIGN.md YAML frontmatter 구문 오류", null);
        when(designMdRuleLoader.load(any())).thenReturn(ThymeleafGenerationStageResult.failure(List.of(fatal)));

        var result = sut.process(processingContext(css, "profile-1"));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(css)).isEqualTo(before);
    }

    private GenerationProcessingContext processingContext(Path css, String profileId) {
        GenerationContext context = context(Map.of(
                CrudGenerationAttributes.DESIGN_SYSTEM_PROFILE_ID, profileId));
        GenerationBlueprint blueprint = new GenerationBlueprint(context, List.of(), List.of(), List.of());
        return GenerationProcessingContext.beforeRender(blueprint);
    }

    private GenerationContext context(Map<String, Object> attributes) {
        return new GenerationContext(
                "crud", "com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", projectRoot.toString(), "5.0", "thymeleaf", attributes);
    }
}
