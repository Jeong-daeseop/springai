package com.krdevops.springai.service.generation.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WP7 2차 pass/ARCH-0716: CRUD Pipeline의 유일한 WRITE 어댑터가 공용 {@code ApprovedProjectWritePort}
 * ({@link com.krdevops.springai.model.write.ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY})로 전환된
 * 뒤에도, 옛 {@code codeService.saveGeneratedCode} 직접 호출과 동일한 외부 동작(파일별 독립 저장,
 * allowlist 검증, 렌더 실패 보존)을 유지하는지 검증한다.
 */
class CodeServiceGenerationExecutorTest {

    @TempDir
    Path outputRoot;

    private CodeServiceGenerationExecutor executor(Path basePath) {
        CodeService codeService = new CodeService(egovProperties(basePath));
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new CodeServiceGenerationExecutor(codeService, writePort);
    }

    @Test
    void execute_writesRenderedFileAndPreservesPreExistingRenderFailure() {
        CodeServiceGenerationExecutor executor = executor(outputRoot);
        RenderedFilePlan rendered = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null),
                "class EmployerVO {}");
        GenerationFailure renderFailure = new GenerationFailure("controller", "렌더 실패");
        RenderedFilePlan failedToRender = RenderedFilePlan.failed(
                new FileBlueprint("controller", "EmployerController.java",
                        outputRoot.resolve("EmployerController.java"), null),
                renderFailure);
        RenderedGenerationPlan plan = new RenderedGenerationPlan(
                context(outputRoot), List.of(rendered, failedToRender), List.of(), List.of());

        GenerationExecution execution = executor.execute(plan);

        assertThat(execution.succeededFiles()).containsExactly(rendered);
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent("class EmployerVO {}");
        assertThat(execution.failedFiles()).containsExactly(renderFailure);
    }

    @Test
    void execute_continuesPastIndividualWriteFailureAndKeepsSucceededFile() throws Exception {
        CodeServiceGenerationExecutor executor = executor(outputRoot);
        // "blocked"를 파일로 만들어 blocked/Controller.java의 부모 디렉터리 생성이 실패하게 한다.
        Files.writeString(outputRoot.resolve("blocked"), "parent-is-a-file");
        RenderedFilePlan good = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null),
                "class EmployerVO {}");
        RenderedFilePlan blocked = RenderedFilePlan.rendered(
                new FileBlueprint("controller", "EmployerController.java",
                        outputRoot.resolve("blocked/EmployerController.java"), null),
                "class EmployerController {}");
        RenderedGenerationPlan plan = new RenderedGenerationPlan(
                context(outputRoot), List.of(good, blocked), List.of(), List.of());

        GenerationExecution execution = executor.execute(plan);

        assertThat(execution.succeededFiles()).containsExactly(good);
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent("class EmployerVO {}");
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("controller");
    }

    @Test
    void execute_rejectsOutputPathOutsideAllowedLocations(@TempDir Path elsewhere) {
        Path basePath = outputRoot.resolve("allowed-base");
        CodeServiceGenerationExecutor executor = executor(basePath);
        RenderedFilePlan file = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", elsewhere.resolve("EmployerVO.java"), null),
                "class EmployerVO {}");
        RenderedGenerationPlan plan = new RenderedGenerationPlan(
                context(elsewhere), List.of(file), List.of(), List.of());

        assertThatThrownBy(() -> executor.execute(plan)).isInstanceOf(SecurityException.class);
        assertThat(elsewhere.resolve("EmployerVO.java")).doesNotExist();
    }

    private GenerationContext context(Path outputPath) {
        return new GenerationContext(
                "crud", "ebt", "EMP", "emp", "egovframework.let.emp",
                outputPath.toString(), "5.0", "thymeleaf", Map.of());
    }

    private EgovProperties egovProperties(Path basePath) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(basePath.toString());
        properties.setOutput(output);
        return properties;
    }
}
