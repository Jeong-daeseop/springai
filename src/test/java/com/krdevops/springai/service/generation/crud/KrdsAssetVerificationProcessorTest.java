package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class KrdsAssetVerificationProcessorTest {

    private static final String WAR_CSS = "src/main/webapp/resources/css/_ds_bundle.css";
    private static final String WAR_JS = "src/main/webapp/resources/js/krds.min.js";
    private static final String BOOT_CSS = "src/main/resources/static/resources/css/_ds_bundle.css";
    private static final String BOOT_JS = "src/main/resources/static/resources/js/krds.min.js";

    @TempDir
    Path projectRoot;

    private final KrdsAssetVerificationProcessor sut = new KrdsAssetVerificationProcessor();

    @Test
    void failsAndStopsLaterProcessorsWhenAssetsAreMissing() {
        AtomicBoolean laterProcessorCalled = new AtomicBoolean();
        GenerationStageProcessor laterProcessor = processor("laterProcessor", laterProcessorCalled);
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(sut, laterProcessor));

        GenerationProcessorRunner.ProcessorRunResult result = runner.run(
                GenerationStage.PRE_WRITE,
                List.of(
                        new ProcessorStep(KrdsAssetVerificationProcessor.ID,
                                GenerationStage.PRE_WRITE, 90, FailurePolicy.STOP),
                        new ProcessorStep("laterProcessor",
                                GenerationStage.PRE_WRITE, 100, FailurePolicy.STOP)),
                processingContext());

        assertThat(result.stopped()).isTrue();
        assertThat(result.stopSummary()).isEqualTo("KRDS 원본 자산 없음");
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.source()).isEqualTo(KrdsAssetVerificationProcessor.ID);
            assertThat(failure.description()).contains("_ds_bundle.css/krds.min.js가 없습니다")
                    .contains("ProjectInitializrTool.initializeProject()");
        });
        assertThat(laterProcessorCalled).isFalse();
    }

    @Test
    void passesWhenWarAssetsAreComplete() throws IOException {
        createAsset(WAR_CSS);
        createAsset(WAR_JS);

        assertThat(sut.process(processingContext()).success()).isTrue();
    }

    @Test
    void passesWhenBootAssetsAreComplete() throws IOException {
        createAsset(BOOT_CSS);
        createAsset(BOOT_JS);

        assertThat(sut.process(processingContext()).success()).isTrue();
    }

    @Test
    void failsWhenAssetsAreSplitAcrossWarAndBootLocations() throws IOException {
        createAsset(WAR_CSS);
        createAsset(BOOT_JS);

        ProcessorResult result = sut.process(processingContext());

        assertThat(result.success()).isFalse();
        assertThat(result.failureSummary()).isEqualTo("KRDS 원본 자산 없음");
    }

    private void createAsset(String relativePath) throws IOException {
        Path asset = projectRoot.resolve(relativePath);
        Files.createDirectories(asset.getParent());
        Files.createFile(asset);
    }

    private GenerationProcessingContext processingContext() {
        GenerationContext context = new GenerationContext(
                "crud", "com", "LETTNEMPLYRINFO", "Employer",
                "egovframework.let.emp", projectRoot.toString(), "5.0", "thymeleaf", Map.of());
        GenerationBlueprint blueprint = new GenerationBlueprint(context, List.of(), List.of(), List.of());
        return GenerationProcessingContext.beforeRender(blueprint);
    }

    private static GenerationStageProcessor processor(String id, AtomicBoolean called) {
        return new GenerationStageProcessor() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public GenerationStage stage() {
                return GenerationStage.PRE_WRITE;
            }

            @Override
            public boolean supports(GenerationContext context) {
                return true;
            }

            @Override
            public ProcessorResult process(GenerationProcessingContext context) {
                called.set(true);
                return ProcessorResult.ok();
            }
        };
    }
}
