package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.BoardRouteCollisionDetector;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.CodeValidatorService;
import com.krdevops.springai.service.CrudModelFactory;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudSchemaQueryService;
import com.krdevops.springai.service.CrudTemplateRenderer;
import com.krdevops.springai.service.GeneratedCodeContractAuditor;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.GenerationHistoryService;
import com.krdevops.springai.service.KrdsStylesConfigurer;
import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.WarEntryPointConfigurer;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.processor.CodeDirectoryVerifier;
import com.krdevops.springai.service.generation.pipeline.processor.CodeServiceGenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.processor.CommonGeneratedContractVerifier;
import com.krdevops.springai.service.generation.pipeline.processor.ControllerScanProcessor;
import com.krdevops.springai.service.generation.pipeline.processor.DefaultGenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.processor.MyBatisRuntimeProcessor;
import com.krdevops.springai.service.generation.pipeline.processor.ThymeleafRuntimeProcessor;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver;
import com.krdevops.springai.service.thymeleaf.DesignMdRuleLoader;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Spring 컨텍스트 없이 CRUD 생성 Pipeline 전체를 실제 구현체로 조립하는 테스트 헬퍼.
 *
 * <p>Bean 등록 순서에 의존하지 않는다는 점을 드러내기 위해 Verifier는 일부러 실행 순서와 반대로
 * (Contract → Directory) 넘긴다 — {@link GenerationVerifierRunner}가 stage/order로 재정렬해
 * Directory 검증이 먼저 실행되어야 한다.
 *
 * <p>WP7 2차 pass: {@code CodeServiceGenerationExecutor}가 실제 파일 저장을
 * {@link ApprovedProjectWritePort}에 위임하게 되면서, 이 fixture를 쓰는 테스트는 이제
 * {@code codeService.saveGeneratedCode(...)}가 아니라 {@code writePort.apply(...)}를 mock해야
 * 한다 — {@link #alwaysSucceeds()}/{@link #failingPaths}를 사용하라.
 */
public final class CrudPipelineFixture {

    private CrudPipelineFixture() {
    }

    public static CrudGenerationApplicationService applicationService(
            CrudSchemaQueryService crudSchemaQueryService,
            CrudProgramMetadataService crudProgramMetadataService,
            GenerationDesignContextService generationDesignContextService,
            CrudModelFactory crudModelFactory,
            ThymeleafLayoutValidator thymeleafLayoutValidator,
            BoardRouteCollisionDetector routeCollisionDetector,
            CrudTemplateRenderer crudTemplateRenderer,
            CodeService codeService,
            ApprovedProjectWritePort writePort,
            KrdsStylesConfigurer krdsStylesConfigurer,
            WarEntryPointConfigurer warEntryPointConfigurer,
            ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer,
            MyBatisRuntimeConfigurer myBatisRuntimeConfigurer,
            CodeValidatorService codeValidatorService,
            GeneratedCodeContractAuditor generatedCodeContractAuditor,
            GenerationHistoryService generationHistoryService) {

        return new CrudGenerationApplicationService(
                new CrudGenerationPlanner(crudSchemaQueryService, crudProgramMetadataService,
                        generationDesignContextService, crudModelFactory, thymeleafLayoutValidator,
                        routeCollisionDetector),
                new CrudGenerationRenderer(crudTemplateRenderer),
                new CodeServiceGenerationExecutor(codeService, writePort),
                new GenerationProcessorRunner(List.of(
                        new KrdsAssetVerificationProcessor(),
                        new CrudDesignMdCssProcessor(
                                Mockito.mock(DesignMdRuleLoader.class),
                                Mockito.mock(CompanyDesignTokenResolver.class),
                                krdsStylesConfigurer),
                        new CrudTableDensityCssProcessor(krdsStylesConfigurer),
                        new CrudFormColumnCssProcessor(krdsStylesConfigurer),
                        new CrudEntryPointProcessor(warEntryPointConfigurer,
                                new com.krdevops.springai.service.generation.layout.ProjectTypeDetector()),
                        new ThymeleafRuntimeProcessor(thymeleafRuntimeConfigurer),
                        new ControllerScanProcessor(thymeleafRuntimeConfigurer),
                        new MyBatisRuntimeProcessor(myBatisRuntimeConfigurer))),
                new GenerationVerifierRunner(List.of(
                        new CommonGeneratedContractVerifier(generatedCodeContractAuditor),
                        new CodeDirectoryVerifier(codeValidatorService))),
                new DefaultGenerationHistoryRecorder(generationHistoryService),
                new CrudGenerationResultAssembler());
    }

    /** mock된 {@link ApprovedProjectWritePort#apply}가 항상 전체 성공한 것처럼 응답하게 한다. */
    public static Answer<ApplyOutcome> alwaysSucceeds() {
        return failingPaths(path -> false, null);
    }

    /** 테스트 프로젝트를 KRDS 원본 자산이 완비된 WAR fixture로 만든다. */
    public static void createWarKrdsAssets(Path projectRoot) {
        try {
            createAsset(projectRoot.resolve("src/main/webapp/resources/css/_ds_bundle.css"));
            createAsset(projectRoot.resolve("src/main/webapp/resources/js/krds.min.js"));
        } catch (IOException e) {
            throw new IllegalStateException("KRDS 테스트 자산 생성 실패: " + projectRoot, e);
        }
    }

    private static void createAsset(Path asset) throws IOException {
        Files.createDirectories(asset.getParent());
        if (Files.notExists(asset)) {
            Files.createFile(asset);
        }
    }

    /**
     * mock된 {@link ApprovedProjectWritePort#apply}가 {@code shouldFail}에 걸리는 상대경로만
     * {@code failureMessage}로 실패 처리하고 나머지는 성공한 것처럼 응답하게 한다.
     */
    public static Answer<ApplyOutcome> failingPaths(Predicate<String> shouldFail, String failureMessage) {
        return invocation -> {
            ProjectChangeSet changeSet = invocation.getArgument(0);
            List<String> applied = new ArrayList<>();
            Map<String, String> failures = new LinkedHashMap<>();
            for (ProjectChangeSet.FileChange change : changeSet.generatedFiles()) {
                if (shouldFail.test(change.path())) {
                    failures.put(change.path(), failureMessage);
                } else {
                    applied.add(change.path());
                }
            }
            return failures.isEmpty() ? ApplyOutcome.applied(applied, null) : ApplyOutcome.partiallyApplied(applied, failures);
        };
    }
}
