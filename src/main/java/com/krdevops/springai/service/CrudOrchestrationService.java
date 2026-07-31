package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.service.generation.api.GenerateCrudProjectUseCase;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 기존 호출자를 위한 Compatibility Facade. 명세서 §15.1.
 *
 * <p>실제 생성 로직은 {@link GenerateCrudProjectUseCase} 구현체(Planner→Renderer→Executor→
 * Processor→Verifier→HistoryRecorder Pipeline)로 이전했다. 이 클래스는 4개 오버로드를
 * {@link CrudGenerationCommand}로 변환해 위임하기만 한다.
 *
 * @deprecated 신규 코드는 {@link GenerateCrudProjectUseCase}를 직접 사용한다.
 */
@Deprecated
@Service
@RequiredArgsConstructor
public class CrudOrchestrationService {

    private final GenerateCrudProjectUseCase generateCrudProjectUseCase;

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, "jsp");
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                null, null, null);
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView) {
        return orchestrate(database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                layoutMode, layoutView, breadcrumbView, CrudGenerationOptions.empty());
    }

    public CrudOrchestrationResult orchestrate(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView,
            CrudGenerationOptions options) {
        return generateCrudProjectUseCase.execute(toCommand(
                database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                layoutMode, layoutView, breadcrumbView, options));
    }

    private static CrudGenerationCommand toCommand(
            String database, String tableName, String domain, String packageName,
            String outputPath, String egovVersion, String viewType,
            String layoutMode, String layoutView, String breadcrumbView,
            CrudGenerationOptions options) {

        CrudGenerationOptions resolved = options == null ? CrudGenerationOptions.empty() : options;
        return new CrudGenerationCommand(
                database, tableName, domain, packageName, Path.of(outputPath), "auto", egovVersion, viewType,
                new LayoutOptions(layoutMode, layoutView, breadcrumbView),
                new ProgramMetadataOverrides(resolved.programFileName(), resolved.programUrl(),
                        resolved.programKoreanName(), resolved.programStorePath()),
                new DesignContextReference(
                        resolved.designReferenceId(), resolved.screenSpecificationId()));
    }
}
