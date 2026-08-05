package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.generation.api.GenerateThymeleafLayoutUseCase;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult.FileOutcome;
import com.krdevops.springai.service.generation.layout.ThymeleafLayoutGenerationPlanner.PlannedFile;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Layout Use Case 조율 — Planner 호출 → 파일별 렌더 → 배치 저장 → Asset 복사 →
 * Servlet/MyBatis/Runtime Processor 호출 → {@link LayoutGenerationResult} 조립.
 *
 * <p>WP7 2차 pass 잔여 항목/ARCH-0718: layout 5종/GNB 4종/main.html은 예전에는 파일마다
 * {@code codeService.saveGeneratedCode}를 즉시 호출했지만, 이제 전부 렌더링해 모은 뒤 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} — 파일 하나가
 * 실패해도 나머지는 계속 저장하는 기존 보장을 그대로 유지)로 한 번에 배치 적용한다.
 * {@link ThymeleafLayoutGenerationPlanner}가 이미 skip(보존) 여부를 렌더링과 분리해 계산해 두므로,
 * 이 배치 전환은 저장 호출 지점만 바꾸는 기계적 변경이다 — 저장 이후 원래 Planner 순서 그대로
 * {@link FileOutcome} 목록을 재구성한다({@code CodeServiceGenerationExecutor}와 동일한 순서 보존
 * 패턴).
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
    private final ApprovedProjectWritePort writePort;

    @Override
    public LayoutGenerationResult generate(GenerateThymeleafLayoutCommand command) {
        Path outputPath = command.outputPath();
        String resolvedBasePath = command.layoutBasePath();
        boolean overwrite = command.overwrite();
        String resolvedPackageName = command.packageName();
        String resolvedMenuTableName = command.menuTableName();
        String resolvedProgramTableName = command.programTableName();

        codeService.validateOutputRoot(outputPath.toString());

        List<PlannedFile> layoutPlanned = planner.planLayoutFiles(outputPath, resolvedBasePath, overwrite);
        List<PlannedFile> gnbPlanned = planner.planGnbComponents(outputPath, resolvedPackageName, overwrite);
        PlannedFile mainPlanned = planner.planMainHtml(outputPath, overwrite);

        List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
        for (PlannedFile plannedFile : layoutPlanned) {
            if (!plannedFile.skip()) {
                String code = crudTemplateRenderer.renderLayoutByLayerKey(plannedFile.layerKey(), resolvedBasePath);
                addChange(changes, outputPath, plannedFile.path(), code);
            }
        }
        for (PlannedFile plannedFile : gnbPlanned) {
            if (!plannedFile.skip()) {
                String code = crudTemplateRenderer.renderGnbMenuComponent(
                        plannedFile.layerKey(), resolvedPackageName, resolvedMenuTableName, resolvedProgramTableName);
                addChange(changes, outputPath, plannedFile.path(), code);
            }
        }
        if (!mainPlanned.skip()) {
            String html = mainPageRenderer.render(resolvedBasePath + "/default", resolvedBasePath + "/breadcrumb");
            addChange(changes, outputPath, mainPlanned.path(), html);
        }

        Map<String, String> failureMessagesByRelative = Map.of();
        if (!changes.isEmpty()) {
            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath.toString(), null, changes, List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            failureMessagesByRelative = writePort.apply(changeSet).failureMessages();
        }

        List<FileOutcome> layoutOutcomes = reconstructOutcomes(outputPath, layoutPlanned, failureMessagesByRelative);
        List<FileOutcome> gnbComponentOutcomes = reconstructOutcomes(outputPath, gnbPlanned, failureMessagesByRelative);
        FileOutcome mainHtmlOutcome = reconstructOutcome(outputPath, mainPlanned, failureMessagesByRelative);

        String logoResultLine = classpathAssetCopier.copyLogo(outputPath, overwrite);

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

    private void addChange(List<ProjectChangeSet.FileChange> changes, Path outputRoot, Path targetPath, String content) {
        String relative = outputRoot.relativize(targetPath).toString();
        changes.add(new ProjectChangeSet.FileChange(relative, null, content, null));
    }

    private List<FileOutcome> reconstructOutcomes(
            Path outputRoot, List<PlannedFile> planned, Map<String, String> failureMessagesByRelative) {
        List<FileOutcome> outcomes = new ArrayList<>();
        for (PlannedFile plannedFile : planned) {
            outcomes.add(reconstructOutcome(outputRoot, plannedFile, failureMessagesByRelative));
        }
        return outcomes;
    }

    private FileOutcome reconstructOutcome(
            Path outputRoot, PlannedFile plannedFile, Map<String, String> failureMessagesByRelative) {
        if (plannedFile.skip()) {
            return FileOutcome.preserved(plannedFile.path());
        }
        String relative = outputRoot.relativize(plannedFile.path()).toString();
        String failureMessage = failureMessagesByRelative.get(relative);
        if (failureMessage != null) {
            return FileOutcome.failed(plannedFile.path(), failureMessage);
        }
        return FileOutcome.created(plannedFile.path());
    }
}
