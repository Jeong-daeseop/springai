package com.krdevops.springai.service.generation.pipeline;

/**
 * 생성 이력을 남긴다(HISTORY 단계). 명세서 §10.8.
 *
 * <p>이력 저장 실패는 비치명이다 — 이미 성공한 파일 생성을 취소하지 않고 요약 문자열로만 보고한다.
 */
public interface GenerationHistoryRecorder {

    HistoryRecordResult record(GenerationProcessingContext context);
}
