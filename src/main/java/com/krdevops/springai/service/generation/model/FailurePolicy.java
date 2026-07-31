package com.krdevops.springai.service.generation.model;

/**
 * Processor 실패 시 Pipeline이 취할 행동. 명세서 §10.1.
 *
 * <ul>
 *   <li>{@link #STOP} — 즉시 Pipeline 전체를 중단하고 실패 결과를 조립한다.</li>
 *   <li>{@link #CONTINUE} — 실패를 누적만 하고 다음 Processor를 계속 실행한다.</li>
 *   <li>{@link #SKIP_DEPENDENTS} — 실패를 누적하고, 이 Processor에 {@code dependsOn}으로
 *       연결된 후속 Processor만 건너뛴다.</li>
 * </ul>
 */
public enum FailurePolicy {
    STOP,
    CONTINUE,
    SKIP_DEPENDENTS
}
