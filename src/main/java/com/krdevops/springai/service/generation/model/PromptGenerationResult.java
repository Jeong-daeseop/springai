package com.krdevops.springai.service.generation.model;

/** Claude가 직접 코드를 작성하도록 안내하는 Prompt 문자열 결과. CRUD/마스터-디테일 공용. */
public record PromptGenerationResult(String prompt) {}
