package com.krdevops.springai.service.generation.model;

/**
 * 생성 Pipeline의 실행 단계. 명세서 §10.1.
 *
 * <p>선언 순서가 곧 실행 순서다 — {@code GenerationProcessorRunner}/{@code GenerationVerifierRunner}가
 * enum ordinal을 1차 정렬 키로 사용한다.
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
