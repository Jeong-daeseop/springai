package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 기존 {@link CodeService#saveGeneratedCode}를 감싸는 유일한 WRITE 어댑터.
 *
 * <p>파일 하나가 실패해도 다음 파일 저장을 계속하며, 이미 저장된 파일을 자동 삭제하지 않는다.
 * {@code "파일 저장 실패"} 접두어로 실패를 판정하는 기존 규약을 그대로 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeServiceGenerationExecutor implements GenerationExecutor {

    private static final String SAVE_FAILURE_PREFIX = "파일 저장 실패";

    private final CodeService codeService;

    @Override
    public GenerationExecution execute(RenderedGenerationPlan plan) {
        List<RenderedFilePlan> succeeded = new ArrayList<>();
        List<GenerationFailure> failed = new ArrayList<>();

        for (RenderedFilePlan file : plan.files()) {
            if (!file.rendered()) {
                failed.add(file.renderFailure());
                continue;
            }
            String filePath = file.targetPath().toString();
            try {
                String saveResult = codeService.saveGeneratedCode(filePath, file.source());
                if (saveResult.startsWith(SAVE_FAILURE_PREFIX)) {
                    failed.add(new GenerationFailure(
                            file.layerKey(), file.displayName() + " — " + saveResult));
                    log.error("[pipeline] 저장 실패: {}", filePath);
                } else {
                    succeeded.add(file);
                    log.info("[pipeline] 저장 완료: {}", filePath);
                }
            } catch (Exception e) {
                failed.add(new GenerationFailure(
                        file.layerKey(), file.displayName() + " — 오류: " + e.getMessage()));
                log.error("[pipeline] 저장 실패: layer={}, error={}", file.layerKey(), e.getMessage());
            }
        }
        return new GenerationExecution(plan, succeeded, failed);
    }
}
