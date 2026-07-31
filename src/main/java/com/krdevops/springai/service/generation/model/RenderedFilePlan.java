package com.krdevops.springai.service.generation.model;

import java.nio.file.Path;

/**
 * 렌더링을 마친 파일 1개의 계획. 명세서 §10.4.
 *
 * <p>렌더링에 실패한 레이어도 목록에서 빠지지 않고 {@link #renderFailure}가 채워진 채로 남는다 —
 * Executor가 레이어 순서 그대로 순회하면서 실패를 누적해야 기존의 성공/실패 목록 순서가
 * 보존되기 때문이다.
 */
public record RenderedFilePlan(
        String layerKey,
        String displayName,
        Path targetPath,
        String source,
        GenerationFailure renderFailure
) {
    public boolean rendered() {
        return renderFailure == null;
    }

    public static RenderedFilePlan rendered(FileBlueprint file, String source) {
        return new RenderedFilePlan(file.layerKey(), file.displayName(), file.targetPath(), source, null);
    }

    public static RenderedFilePlan failed(FileBlueprint file, GenerationFailure failure) {
        return new RenderedFilePlan(file.layerKey(), file.displayName(), file.targetPath(), null, failure);
    }
}
