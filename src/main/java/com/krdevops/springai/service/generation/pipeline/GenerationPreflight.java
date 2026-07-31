package com.krdevops.springai.service.generation.pipeline;

/**
 * 파일을 쓰기 전에 수행하는 사전 검증. 명세서 §10.2.
 *
 * <p>검증 항목: 필수 입력, Package Name 규칙, Schema 존재, Metadata 충돌, Route 충돌,
 * Layout 존재, Output Path 정책.
 */
public interface GenerationPreflight<C> {

    PreflightResult validate(C command);
}
