package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.FileOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafLayoutResultFormatterTest {

    private final ThymeleafLayoutResultFormatter formatter = new ThymeleafLayoutResultFormatter();

    @Test
    void format_runtimeSkipped_omitsRuntimeConfiguredMessage() {
        LayoutGenerationResult result = baseResultBuilder(true, List.of());

        String output = formatter.format(result);

        assertThat(output)
                .contains("[Thymeleaf 런타임 설정]")
                .contains("건너뜀: servlet-context.xml patch 실패 상태라 ViewResolver 보강을 생략합니다.")
                .doesNotContain("완료: eGovFrame");
    }

    @Test
    void format_runtimeSuccess_includesCompletionMessage() {
        LayoutGenerationResult result = baseResultBuilder(false, List.of());

        String output = formatter.format(result);

        assertThat(output).contains("완료: eGovFrame 5.0 기준 Thymeleaf ViewResolver/classpath:/templates 런타임 설정을 확인했습니다.");
    }

    @Test
    void format_runtimeFailures_listsEachFailureLine() {
        LayoutGenerationResult result = baseResultBuilder(false, List.of("실패 원인 A", "실패 원인 B"));

        String output = formatter.format(result);

        assertThat(output).contains("  실패: 실패 원인 A\n").contains("  실패: 실패 원인 B\n");
    }

    @Test
    void format_packageNameMissing_prependsWarningWithResolvedDefault() {
        LayoutGenerationResult result = baseResultBuilder(false, List.of());
        LayoutGenerationResult missingResult = new LayoutGenerationResult(
                result.outputPath(), result.resolvedBasePath(), "egovframework.let.sample", true,
                result.resolvedMenuTableName(), result.resolvedProgramTableName(), result.layoutFileOutcomes(),
                result.logoResultLine(), result.gnbComponentOutcomes(), result.mainHtmlOutcome(), result.validation(),
                result.servletContextPatchMessage(), result.servletContextPatchFailed(), result.myBatisResult(),
                result.egovVersion(), result.runtimeSkipped(), result.runtimeFailures());

        String output = formatter.format(missingResult);

        assertThat(output).contains("⚠ packageName 미지정 — 기본값 'egovframework.let.sample' 사용.");
    }

    private LayoutGenerationResult baseResultBuilder(boolean runtimeSkipped, List<String> runtimeFailures) {
        Path outputPath = Path.of("/tmp/project");
        Path layoutFile = outputPath.resolve("src/main/resources/templates/layout/default.html");
        return new LayoutGenerationResult(
                outputPath.toString(),
                "layout",
                "egovframework.let.emp",
                false,
                "LETTNMENUINFO",
                "LETTNPROGRMLIST",
                List.of(FileOutcome.created(layoutFile)),
                "  생성: " + outputPath.resolve("src/main/webapp/resources/images/egov-logo.png") + "\n",
                List.of(FileOutcome.created(outputPath.resolve("src/main/java/egovframework/let/emp/cmm/vo/GnbMenuVO.java"))),
                FileOutcome.created(outputPath.resolve("src/main/resources/templates/egovframework/main/main.html")),
                new ThymeleafLayoutValidator.LayoutValidationResult(
                        new ThymeleafLayoutValidator.LayoutReference("layout/default", "layout/breadcrumb", "layout"),
                        List.of()),
                "  등록: servlet-context.xml 에 EgovGnbMenuInterceptor patch 완료\n",
                false,
                new MyBatisRuntimeConfigurer.ConfigurationResult(
                        true, true, false,
                        outputPath.resolve("src/main/resources/egovframework/spring/context-common.xml"),
                        "보강 완료"),
                "5.0",
                runtimeSkipped,
                runtimeFailures);
    }
}
