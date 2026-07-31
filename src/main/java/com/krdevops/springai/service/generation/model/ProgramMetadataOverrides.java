package com.krdevops.springai.service.generation.model;

/**
 * LETTNPROGRMLIST 프로그램 메타데이터 명시 오버라이드 — CRUD/게시판 Command가 공유하는 공용 Value Object.
 * 명세서 §8.1.
 */
public record ProgramMetadataOverrides(
        String programFileName,
        String programUrl,
        String programKoreanName,
        String programStorePath
) {
    public static ProgramMetadataOverrides empty() {
        return new ProgramMetadataOverrides(null, null, null, null);
    }
}
