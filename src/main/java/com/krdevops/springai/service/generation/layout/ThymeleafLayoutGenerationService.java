package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.generation.api.GenerateThymeleafLayoutUseCase;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.FileOutcome;
import com.krdevops.springai.service.generation.layout.ThymeleafLayoutGenerationPlanner.PlannedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout Use Case 조율 — Planner 호출 → 파일별 렌더/저장 → Asset 복사 →
 * Servlet/MyBatis/Runtime Processor 호출 → {@link LayoutGenerationResult} 조립.
 */
@Service
@RequiredArgsConstructor
public class ThymeleafLayoutGenerationService implements GenerateThymeleafLayoutUseCase {

    private static final String DEFAULT_EGOV_VERSION = "5.0";

    private final CrudTemplateRenderer crudTemplateRenderer;
    private final CodeService codeService;
    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;
    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;
    private final ThymeleafLayoutGenerationPlanner planner;
    private final MainPageRenderer mainPageRenderer;
    private final ClasspathAssetCopier classpathAssetCopier;
    private final ServletContextConfigurer servletContextConfigurer;

    @Override
    public LayoutGenerationResult generate(GenerateThymeleafLayoutCommand command) {
        Path outputPath = command.outputPath();
        String resolvedBasePath = command.layoutBasePath();
        boolean overwrite = command.overwrite();
        String resolvedPackageName = command.packageName();
        String resolvedMenuTableName = command.menuTableName();
        String resolvedProgramTableName = command.programTableName();

        List<FileOutcome> layoutOutcomes = new ArrayList<>();
        for (PlannedFile plannedFile : planner.planLayoutFiles(outputPath, resolvedBasePath, overwrite)) {
            if (plannedFile.skip()) {
                layoutOutcomes.add(FileOutcome.preserved(plannedFile.path()));
                continue;
            }
            String code = crudTemplateRenderer.renderLayoutByLayerKey(plannedFile.layerKey(), resolvedBasePath);
            layoutOutcomes.add(saveCode(plannedFile.path(), code));
        }

        String logoResultLine = classpathAssetCopier.copyLogo(outputPath, overwrite);

        List<FileOutcome> gnbComponentOutcomes = new ArrayList<>();
        for (PlannedFile plannedFile : planner.planGnbComponents(outputPath, resolvedPackageName, overwrite)) {
            if (plannedFile.skip()) {
                gnbComponentOutcomes.add(FileOutcome.preserved(plannedFile.path()));
                continue;
            }
            String code = crudTemplateRenderer.renderGnbMenuComponent(
                    plannedFile.layerKey(), resolvedPackageName, resolvedMenuTableName, resolvedProgramTableName);
            gnbComponentOutcomes.add(saveCode(plannedFile.path(), code));
        }

        PlannedFile mainPlan = planner.planMainHtml(outputPath, overwrite);
        FileOutcome mainHtmlOutcome;
        if (mainPlan.skip()) {
            mainHtmlOutcome = FileOutcome.preserved(mainPlan.path());
        } else {
            String html = mainPageRenderer.render(resolvedBasePath + "/default", resolvedBasePath + "/breadcrumb");
            mainHtmlOutcome = saveCode(mainPlan.path(), html);
        }

        ThymeleafLayoutValidator.LayoutValidationResult validation = thymeleafLayoutValidator.validateExisting(
                outputPath.toString(),
                resolvedBasePath + "/default",
                resolvedBasePath + "/breadcrumb");

        ServletContextConfigurer.ServletContextPatchResult servletPatch =
                servletContextConfigurer.patch(outputPath, resolvedPackageName);

        MyBatisRuntimeConfigurer.ConfigurationResult myBatisResult = myBatisRuntimeConfigurer.ensureConfigured(
                outputPath.toString(), resolvedPackageName + ".cmm.service");

        boolean runtimeSkipped = servletPatch.failed();
        List<String> runtimeFailures = new ArrayList<>();
        if (!runtimeSkipped) {
            thymeleafRuntimeConfigurer.ensureThymeleafRuntime(outputPath.toString(), DEFAULT_EGOV_VERSION, runtimeFailures);
        }

        return new LayoutGenerationResult(
                outputPath.toString(),
                resolvedBasePath,
                resolvedPackageName,
                resolvedMenuTableName,
                resolvedProgramTableName,
                layoutOutcomes,
                logoResultLine,
                gnbComponentOutcomes,
                mainHtmlOutcome,
                validation,
                servletPatch.message(),
                myBatisResult,
                DEFAULT_EGOV_VERSION,
                runtimeSkipped,
                runtimeFailures);
    }

    private FileOutcome saveCode(Path path, String code) {
        String saveResult = codeService.saveGeneratedCode(path.toString(), code);
        if (saveResult.startsWith("파일 저장 실패")) {
            return FileOutcome.failed(path, saveResult);
        }
        return FileOutcome.created(path);
    }
}
