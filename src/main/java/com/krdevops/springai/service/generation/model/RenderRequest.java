package com.krdevops.springai.service.generation.model;

/**
 * "무엇을 렌더링할지"에 대한 계획. 기능별 {@code GenerationRenderer} 구현체가 자신이 아는
 * 구현 타입으로 캐스팅해 사용한다 — {@link GenerationBlueprint}는 렌더링 결과 Source 문자열을
 * 보유하지 않는다(명세서 §10.3).
 */
public interface RenderRequest {
}
