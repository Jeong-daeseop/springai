package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.End2EndConversionPipeline;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.ProjectApplicationResult;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * I-2~I-7 전체 JSP-to-Thymeleaf 변환 파이프라인.
 * 분석(I-2) → 렌더링(I-4) → 적용(I-5) → 배포(I-7)를 하나의 흐름으로 오케스트레이션.
 */
@Service
public class End2EndConversionPipelineService {

    private final LegacyBindingContractAssembler assembler;
    private final ThymeleafSkeletonPlanner skeletonPlanner;
    private final LegacyThymeleafViewComposer viewComposer;
    private final LegacyThymeleafRenderer renderer;
    private final ThymeleafConversionOrchestrationService orchestrationService;
    private final LegacySourceInventoryService inventoryService;
    private final ProjectApplicationService applicationService;

    public End2EndConversionPipelineService(
            LegacyBindingContractAssembler assembler,
            ThymeleafSkeletonPlanner skeletonPlanner,
            LegacyThymeleafViewComposer viewComposer,
            LegacyThymeleafRenderer renderer,
            ThymeleafConversionOrchestrationService orchestrationService,
            LegacySourceInventoryService inventoryService,
            ProjectApplicationService applicationService
    ) {
        this.assembler = assembler;
        this.skeletonPlanner = skeletonPlanner;
        this.viewComposer = viewComposer;
        this.renderer = renderer;
        this.orchestrationService = orchestrationService;
        this.inventoryService = inventoryService;
        this.applicationService = applicationService;
    }

    /**
     * I-2~I-7 전체 파이프라인 실행.
     *
     * @param analysis 분석된 JSP/Controller/VO
     * @param pageTitle 페이지 제목
     * @param targetOutputPath 대상 출력 경로
     * @param projectRoot 프로젝트 루트 (배포 시 필요)
     * @param autoApply true면 자동 승인 후 적용, false면 Preview까지만
     * @return 파이프라인 실행 결과
     */
    public End2EndConversionPipeline execute(
            LegacyScreenAnalysis analysis,
            String pageTitle,
            String targetOutputPath,
            Path projectRoot,
            boolean autoApply
    ) {
        String pipelineId = "e2e-" + UUID.randomUUID();
        List<String> issues = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // I-2: 분석 (이미 완료, analysis 파라미터로 전달)
        long analysisTimeMs = 100; // 분석은 이미 수행됨

        // I-4: 렌더링 (Preview까지)
        long renderStartTime = System.currentTimeMillis();
        ThymeleafGenerationStageResult<ThymeleafConversionOperation> previewResult =
                orchestrationService.analyzeAndPreview(analysis, pageTitle, targetOutputPath);
        long renderingTimeMs = System.currentTimeMillis() - renderStartTime;

        if (!previewResult.successful()) {
            issues.addAll(previewResult.issues().stream()
                    .map(i -> i.code() + ": " + i.message())
                    .toList());
            return buildFailedResult(pipelineId, analysis, null, null, null,
                    End2EndConversionPipeline.PipelineStatus.RENDERING_FAILED,
                    issues, analysisTimeMs, renderingTimeMs, 0, 0,
                    System.currentTimeMillis() - startTime);
        }

        ThymeleafConversionOperation previewOp = previewResult.value();
        String renderedHtml = previewOp.renderedHtml();

        // I-5: Apply (선택사항)
        long applyTimeMs = 0;
        ThymeleafConversionOperation appliedOp = null;

        if (autoApply) {
            // 승인
            ThymeleafConversionOperation approved = orchestrationService.approve(previewOp.operationId());

            // 적용
            long applyStartTime = System.currentTimeMillis();
            ThymeleafGenerationStageResult<ThymeleafConversionOperation> applyResult =
                    orchestrationService.apply(approved.operationId(), projectRoot, analysis);
            applyTimeMs = System.currentTimeMillis() - applyStartTime;

            if (!applyResult.successful()) {
                issues.addAll(applyResult.issues().stream()
                        .map(i -> i.code() + ": " + i.message())
                        .toList());
                return buildFailedResult(pipelineId, analysis, null, renderedHtml, null,
                        End2EndConversionPipeline.PipelineStatus.APPLY_FAILED,
                        issues, analysisTimeMs, renderingTimeMs, applyTimeMs, 0,
                        System.currentTimeMillis() - startTime);
            }
            appliedOp = applyResult.value();
        }

        // I-7: 프로젝트 배포 (선택사항)
        long deploymentTimeMs = 0;
        ProjectApplicationResult deploymentResult = null;

        if (autoApply && appliedOp != null) {
            long deployStartTime = System.currentTimeMillis();
            deploymentResult = applicationService.applyToProject(
                    projectRoot,
                    appliedOp.targetRelativePath(),
                    List.of(appliedOp.targetRelativePath())
            );
            deploymentTimeMs = System.currentTimeMillis() - deployStartTime;

            List<String> deploymentIssues = applicationService.validateDeployment(deploymentResult);
            if (!deploymentIssues.isEmpty()) {
                issues.addAll(deploymentIssues);
            }
        }

        long totalTimeMs = System.currentTimeMillis() - startTime;
        End2EndConversionPipeline.PipelineStatus status = issues.isEmpty()
                ? End2EndConversionPipeline.PipelineStatus.SUCCESS
                : (appliedOp != null ? End2EndConversionPipeline.PipelineStatus.DEPLOYMENT_FAILED
                : End2EndConversionPipeline.PipelineStatus.RENDERING_FAILED);

        ThymeleafBindingContract contract = extractContractFromOperation(previewOp);

        return new End2EndConversionPipeline(
                pipelineId,
                analysis.screenId(),
                analysis,
                contract,
                renderedHtml,
                appliedOp,
                deploymentResult,
                status,
                issues,
                new End2EndConversionPipeline.PipelineMetrics(
                        analysisTimeMs, renderingTimeMs, applyTimeMs, deploymentTimeMs, totalTimeMs
                )
        );
    }

    private End2EndConversionPipeline buildFailedResult(
            String pipelineId,
            LegacyScreenAnalysis analysis,
            ThymeleafBindingContract contract,
            String renderedHtml,
            ProjectApplicationResult deployment,
            End2EndConversionPipeline.PipelineStatus status,
            List<String> issues,
            long analysisTime,
            long renderTime,
            long applyTime,
            long deployTime,
            long totalTime
    ) {
        return new End2EndConversionPipeline(
                pipelineId,
                analysis.screenId(),
                analysis,
                contract,
                renderedHtml,
                null,
                deployment,
                status,
                issues,
                new End2EndConversionPipeline.PipelineMetrics(
                        analysisTime, renderTime, applyTime, deployTime, totalTime
                )
        );
    }

    private ThymeleafBindingContract extractContractFromOperation(ThymeleafConversionOperation op) {
        // 실제 구현에서는 operation에서 contract를 추출
        // 여기서는 null로 처리 (operation이 contract 정보를 갖고 있음)
        return null;
    }
}
