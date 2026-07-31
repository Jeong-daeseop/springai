package com.krdevops.springai.service.generation.model;

/**
 * 생성 Pipeline의 실행 단계. 명세서 §10.1.
 *
 * <p>{@code GenerationVerifierRunner}는 enum ordinal을 1차 정렬 키로 사용해 Verifier를 stage
 * 순서대로 실행한다. 반면 {@code GenerationProcessorRunner}는 ordinal을 전혀 읽지 않는다 —
 * {@code stage}는 호출자가 넘기는 필터 인자일 뿐이며, 그 stage 안에서의 실행 순서는
 * {@code order} → {@code processorId}로만 정렬된다. 즉 Processor 단계 간 실제 실행 순서는 이
 * enum의 선언 순서가 아니라 호출자({@code CrudGenerationApplicationService} 등)가 명시적으로
 * 결정한다.
 *
 * <p><b>주의</b>: {@code PRE_WRITE}는 enum 상 {@code RENDER} 뒤에 있지만, CRUD 실제 동작
 * (WP-0 {@code CrudOrchestrationProcessorOrderTest} 실측)에서는 CSS 보강이 템플릿 렌더링보다
 * 먼저 실행된다. 기존 동작 보존({@code ORT-PRN-005})이 enum 배치보다 우선하므로
 * {@code CrudGenerationApplicationService}는 PRE_WRITE를 렌더링 전에 실행한다.
 */
public enum GenerationStage {
    PREFLIGHT,
    PLAN,
    RENDER,
    PRE_WRITE,
    WRITE,
    POST_WRITE,
    PRE_VERIFY,
    VERIFY,
    HISTORY
}
