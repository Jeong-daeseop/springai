package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>실제 파일 저장은 공용 {@link ApprovedProjectWritePort}에
 * {@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY}로 위임한다 — 파일 하나가 실패해도 다음 파일
 * 저장을 계속하며, 이미 저장된 파일을 자동 삭제하지 않는다는 기존 규약을 그대로 유지한다. 단,
 * {@code outputPath}가 허용된 위치(basePath/workspace/allowedPaths)인지는 여전히
 * {@link CodeService#validateOutputRoot}로 먼저 검증한다 — {@code ApprovedProjectWritePort}는 주어진
 * root 안에서의 이탈만 막지, 그 root 자체가 허용됐는지는 모르기 때문이다(ARCH-0704 project root
 * registry 부재, WP7 1차 pass 메모 참고). 이 검증에 실패하면 {@link SecurityException}을 그대로
 * 던진다 — 승인된 변경으로도 정당화될 수 없는 별도 위반이라 "실패" 결과가 아니라 예외로 표현한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeServiceGenerationExecutor implements GenerationExecutor {

    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;

    @Override
    public GenerationExecution execute(RenderedGenerationPlan plan) {
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

        // 원본 레이어 순서 그대로 성공/실패를 재구성한다 — 렌더 실패와 저장 실패가 섞여도 순서가
        // 보존돼야 한다("부분 실패 — 렌더링 실패와 저장 실패가 섞여도 레이어 순서대로 누적된다").
        // 실제 저장은 위에서 이미 배치로 끝났으므로, 여기서는 결과만 원래 순서로 재조립한다.
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
}
