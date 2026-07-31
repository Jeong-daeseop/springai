package com.krdevops.springai.service.generation.model;

/**
 * 생성 실패 1건. 명세서 §10.5.
 *
 * <p>{@code description}은 결과 VO의 {@code failedFiles}에 그대로 들어가는 최종 문자열이다 —
 * Pipeline은 이 문자열을 재조립하지 않는다. 기존 오류 메시지 형식을 글자 그대로 보존하기 위한
 * 설계다({@code ORT-PRN-008}).
 */
public record GenerationFailure(String source, String description) {
}
