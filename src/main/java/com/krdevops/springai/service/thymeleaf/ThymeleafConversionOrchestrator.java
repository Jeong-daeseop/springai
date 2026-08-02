package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ConversionPipeline;
import com.krdevops.springai.model.thymeleaf.ConversionPipeline.PipelinePhase;
import com.krdevops.springai.model.thymeleaf.ConversionPipeline.PipelineStage;
import com.krdevops.springai.model.thymeleaf.ScreenHtmlSkeleton;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ViewportConstraint;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * I-7A: Thymeleaf 변환 전체 파이프라인 조율자.
 * JSP → Thymeleaf 전체 흐름을 end-to-end로 실행합니다.
 */
@Service
@RequiredArgsConstructor
public class ThymeleafConversionOrchestrator {

    private final ScreenTypeClassifier screenTypeClassifier;
    private final ScreenHtmlSkeletonGenerator skeletonGenerator;
    private final ResponsiveThymeleafTransformer responsiveTransformer;
    private final ValidationGateExecutor validationExecutor;

    /**
     * 전체 변환 파이프라인을 실행합니다.
     */
    /**
     * 구형 단일 파일 변환 호환 경로. 외부 호출자는 승인·rollback Gate가 있는
     * {@link ThymeleafProjectWorkflowService}를 사용해야 한다.
     */
    @Deprecated(forRemoval = true)
    ConversionPipeline executeConversionPipeline(String jspFilePath, String outputPath) {
        String pipelineId = "pipeline-" + UUID.randomUUID();
        List<PipelineStage> completedStages = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> results = new HashMap<>();
        long startedAt = System.currentTimeMillis();
        PipelinePhase currentPhase = PipelinePhase.INITIALIZED;

        try {
            // Phase 1: JSP 로드
            String jspContent = loadJspFile(jspFilePath);
            completedStages.add(createStage("Load JSP", PipelinePhase.INITIALIZED, jspContent));

            // Phase 2: Screen Decision
            currentPhase = PipelinePhase.SCREEN_DECISION;
            ScreenSpecification specification = inferScreenSpecification(jspFilePath, jspContent);
            var decision = screenTypeClassifier.determineArchetype(specification);
            completedStages.add(createStage("Screen Decision", currentPhase, decision.toString()));
            results.put("screenDecision", decision);
            results.put("screenSpecification", specification);

            // Phase 3: HTML Skeleton 생성
            currentPhase = PipelinePhase.SKELETON_GENERATION;
            ScreenHtmlSkeleton skeleton = skeletonGenerator.generate(specification, decision.archetype());
            completedStages.add(createStage("Skeleton Generation", currentPhase, skeleton.toString()));
            results.put("skeleton", skeleton);

            // Phase 4: Responsive 변환
            currentPhase = PipelinePhase.RESPONSIVE_TRANSFORM;
            String transformedHtml = responsiveTransformer.transformForViewport(
                skeleton,
                ViewportConstraint.desktop()
            );
            completedStages.add(createStage("Responsive Transform", currentPhase, transformedHtml));
            results.put("transformedHtml", transformedHtml);

            // Phase 5: Validation Gate
            currentPhase = PipelinePhase.VALIDATION;
            ValidationGateResult parseResult = validationExecutor.validateThymeleafParse(transformedHtml);
            if (!parseResult.passed()) {
                errors.addAll(parseResult.issues());
            }
            completedStages.add(createStage("Validation", currentPhase, parseResult.toString()));
            results.put("validation", parseResult);

            // Phase 6: 결과 저장
            currentPhase = PipelinePhase.COMPLETED;
            saveConversionResult(outputPath, transformedHtml);
            completedStages.add(createStage("Save Result", currentPhase, outputPath));

            return new ConversionPipeline(
                pipelineId,
                jspFilePath,
                currentPhase,
                completedStages,
                results,
                errors,
                startedAt,
                System.currentTimeMillis()
            );

        } catch (Exception e) {
            errors.add("Pipeline execution failed: " + e.getMessage());
            return new ConversionPipeline(
                pipelineId,
                jspFilePath,
                PipelinePhase.FAILED,
                completedStages,
                results,
                errors,
                startedAt,
                System.currentTimeMillis()
            );
        }
    }

    /**
     * 특정 단계부터 파이프라인 재개.
     */
    public ConversionPipeline resumePipeline(
            ConversionPipeline previousPipeline,
            PipelinePhase fromPhase) {

        List<String> errors = new ArrayList<>(previousPipeline.errors());

        if (fromPhase == PipelinePhase.VALIDATION) {
            // Validation부터 재실행
            Map<String, Object> results = new HashMap<>(previousPipeline.conversionResults());
            String transformedHtml = (String) results.get("transformedHtml");

            ValidationGateResult validateResult = validationExecutor.validateThymeleafParse(transformedHtml);
            results.put("validation", validateResult);

            return new ConversionPipeline(
                previousPipeline.pipelineId(),
                previousPipeline.jspFilePath(),
                validateResult.passed() ? PipelinePhase.COMPLETED : PipelinePhase.FAILED,
                new ArrayList<>(previousPipeline.completedStages()),
                results,
                errors,
                previousPipeline.startedAt(),
                System.currentTimeMillis()
            );
        }

        return previousPipeline;
    }

    /**
     * 변환 결과를 조회합니다.
     */
    public Map<String, Object> getConversionResults(String pipelineId) {
        Map<String, Object> report = new HashMap<>();
        report.put("pipelineId", pipelineId);
        report.put("status", "COMPLETED");
        report.put("generatedAt", System.currentTimeMillis());

        return report;
    }

    // ===== Helper Methods =====

    private String loadJspFile(String jspFilePath) throws Exception {
        Path path = Paths.get(jspFilePath);
        return Files.readString(path);
    }

    private void saveConversionResult(String outputPath, String content) throws Exception {
        Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content);
    }

    /**
     * JSP 단독 입력에서도 이후 단계가 요구하는 최소 ScreenSpecification을 결정적으로 만든다.
     * Controller/VO까지 제공되는 정식 흐름에서는 승인된 ScreenSpecification으로 교체해야 한다.
     */
    private ScreenSpecification inferScreenSpecification(String jspFilePath, String jspContent) {
        String lower = jspContent.toLowerCase(java.util.Locale.ROOT);
        String archetype;
        if (lower.contains("<c:foreach") || lower.contains("<table")) {
            archetype = "LIST";
        } else if (lower.contains("<form") || lower.contains("form:")) {
            archetype = "FORM";
        } else {
            archetype = "DETAIL";
        }

        String fileName = Path.of(jspFilePath).getFileName().toString();
        int extension = fileName.lastIndexOf('.');
        String screenName = extension > 0 ? fileName.substring(0, extension) : fileName;
        PageSpec page = new PageSpec(
                screenName,
                archetype,
                List.of(),
                List.of(),
                FieldSelectionSource.DEFAULT);

        return new ScreenSpecification(
                "legacy-" + screenName,
                1,
                ScreenSpecStatus.DRAFT,
                screenName,
                "CRUD",
                archetype,
                null,
                null,
                List.of(),
                List.of(page),
                List.of(),
                LocalDateTime.now());
    }

    private PipelineStage createStage(String stageName, PipelinePhase phase, String output) {
        long now = System.currentTimeMillis();
        return new PipelineStage(stageName, phase, now, now, true, output);
    }
}
