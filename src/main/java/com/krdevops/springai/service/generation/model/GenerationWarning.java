package com.krdevops.springai.service.generation.model;

/** 생성은 진행되지만 사용자에게 알려야 하는 경고 1건. 명세서 §10.3. */
public record GenerationWarning(String message) {
}
