package com.krdevops.springai.service.generation.model;

import java.util.List;

/**
 * Executor 산출물 — 파일 저장 결과. 명세서 §10.5.
 *
 * <p>파일 하나가 실패해도 나머지 파일 저장은 계속되며, 이미 저장된 파일을 되돌리지 않는다.
 */
public record GenerationExecution(
        RenderedGenerationPlan plan,
        List<RenderedFilePlan> succeededFiles,
        List<GenerationFailure> failedFiles
) {
    public GenerationExecution {
        succeededFiles = succeededFiles == null ? List.of() : List.copyOf(succeededFiles);
        failedFiles = failedFiles == null ? List.of() : List.copyOf(failedFiles);
    }

    public List<String> succeededNames() {
        return succeededFiles.stream().map(RenderedFilePlan::displayName).toList();
    }
}
