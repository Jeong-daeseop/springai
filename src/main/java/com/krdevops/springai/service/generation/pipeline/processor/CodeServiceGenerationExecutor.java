package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WP7 2차 pass/ARCH-0716: CRUD Pipeline 내 유일한 WRITE 어댑터. (Board/Master-Detail Orchestration
 * Service, Thymeleaf Layout 생성 등 이 Pipeline 밖의 다른 경로는 여전히
 * {@code codeService.saveGeneratedCode}를 직접 호출한다 — ARCH-0717/0718 별도 항목.)
 *
 * <p>{@code pipelineEvolutionProperties.usesV2Preview()}가 false면(현재 운영 기본값
 * {@code DUAL_READ} 포함) 기존 {@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} 경로를 그대로
 * 쓴다. true(모드 {@code V2_PREVIEW} 이상)면 Region Ownership 3-way 비교 + Revision drift 감지가
 * 추가된 경로를 탄다 — 상세는 {@code docs/superpowers/specs/2026-08-24-crud-generation-ownership-guard-design.md}.
 */
@Slf4j
@Component
public class CodeServiceGenerationExecutor implements GenerationExecutor {

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final PipelineEvolutionProperties pipelineEvolutionProperties;
    private final CrudGenerationSnapshotStore snapshotStore;

    @Autowired
    public CodeServiceGenerationExecutor(
            CodeService codeService,
            ApprovedProjectWritePort writePort,
            PipelineEvolutionProperties pipelineEvolutionProperties,
            CrudGenerationSnapshotStore snapshotStore) {
        this.codeService = codeService;
        this.writePort = writePort;
        this.pipelineEvolutionProperties = pipelineEvolutionProperties;
        this.snapshotStore = snapshotStore;
    }

    /** Ownership Guard 도입 전 2-arg 호출자·테스트 호환 — usesV2Preview()가 항상 false이므로 legacy 경로만 탄다. */
    public CodeServiceGenerationExecutor(CodeService codeService, ApprovedProjectWritePort writePort) {
        this(codeService, writePort, new PipelineEvolutionProperties(), null);
    }

    @Override
    public GenerationExecution execute(RenderedGenerationPlan plan) {
        if (!pipelineEvolutionProperties.usesV2Preview()) {
            return legacyExecute(plan);
        }
        return ownershipAwareExecute(plan);
    }

    /** 지금까지의 BEST_EFFORT_COMPATIBILITY 경로 — 원문 그대로 옮겨왔다. */
    private GenerationExecution legacyExecute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> toApply = plan.files().stream().filter(RenderedFilePlan::rendered).toList();

        Path outputRoot = null;
        Map<String, String> failureMessagesByRelative = Map.of();
        if (!toApply.isEmpty()) {
            String outputPath = plan.context().outputPath();
            codeService.validateOutputRoot(outputPath);
            outputRoot = Path.of(outputPath);

            List<ProjectChangeSet.FileChange> changes = new ArrayList<>();
            for (RenderedFilePlan file : toApply) {
                String relative = outputRoot.relativize(file.targetPath()).toString();
                changes.add(new ProjectChangeSet.FileChange(relative, null, file.source(), null));
            }

            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath, null, changes, List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            failureMessagesByRelative = writePort.apply(changeSet).failureMessages();
        }

        List<RenderedFilePlan> succeeded = new ArrayList<>();
        List<GenerationFailure> failed = new ArrayList<>();
        for (RenderedFilePlan file : plan.files()) {
            if (!file.rendered()) {
                failed.add(file.renderFailure());
                continue;
            }
            String relative = outputRoot.relativize(file.targetPath()).toString();
            String failureMessage = failureMessagesByRelative.get(relative);
            if (failureMessage != null) {
                failed.add(new GenerationFailure(
                        file.layerKey(), file.displayName() + " — 파일 저장 실패: " + failureMessage));
                log.error("[pipeline] 저장 실패: {}", file.targetPath());
            } else {
                succeeded.add(file);
                log.info("[pipeline] 저장 완료: {}", file.targetPath());
            }
        }

        return new GenerationExecution(plan, succeeded, failed);
    }

    /** Task 6에서 실제 Ownership-aware 로직으로 교체한다. 지금은 legacy와 동일하게 동작한다. */
    private GenerationExecution ownershipAwareExecute(RenderedGenerationPlan plan) {
        return legacyExecute(plan);
    }
}
