package com.krdevops.springai.service.generation.pipeline;

import java.util.List;

/**
 * Preflight 검증 결과. 명세서 §10.2.
 *
 * <p>{@link #passed()}가 false면 파일을 하나도 쓰지 않고 종료한다 — Renderer/Executor/Processor/
 * Verifier가 전혀 실행되지 않아야 한다.
 */
public record PreflightResult(boolean passed, String reasonCode, List<String> messages) {

    public PreflightResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static PreflightResult ok() {
        return new PreflightResult(true, null, List.of());
    }

    public static PreflightResult failed(String reasonCode, List<String> messages) {
        return new PreflightResult(false, reasonCode, messages);
    }
}
