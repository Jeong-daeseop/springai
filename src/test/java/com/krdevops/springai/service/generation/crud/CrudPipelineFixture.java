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

import java.util.List;

/**
 * Spring 컨텍스트 없이 CRUD 생성 Pipeline 전체를 실제 구현체로 조립하는 테스트 헬퍼.
 *
 * <p>Bean 등록 순서에 의존하지 않는다는 점을 드러내기 위해 Verifier는 일부러 실행 순서와 반대로
 * (Contract → Directory) 넘긴다 — {@link GenerationVerifierRunner}가 stage/order로 재정렬해
 * Directory 검증이 먼저 실행되어야 한다.
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
                new CodeServiceGenerationExecutor(codeService),
                new GenerationProcessorRunner(List.of(
                        new CrudTableDensityCssProcessor(krdsStylesConfigurer),
                        new CrudFormColumnCssProcessor(krdsStylesConfigurer),
                        new CrudEntryPointProcessor(warEntryPointConfigurer),
                        new ThymeleafRuntimeProcessor(thymeleafRuntimeConfigurer),
                        new ControllerScanProcessor(thymeleafRuntimeConfigurer),
                        new MyBatisRuntimeProcessor(myBatisRuntimeConfigurer))),
                new GenerationVerifierRunner(List.of(
                        new CommonGeneratedContractVerifier(generatedCodeContractAuditor),
                        new CodeDirectoryVerifier(codeValidatorService))),
                new DefaultGenerationHistoryRecorder(generationHistoryService),
                new CrudGenerationResultAssembler());
    }
}
