package com.krdevops.springai.service.generation.model;

/**
 * Design Reference/ScreenSpecification 참조 — CRUD/게시판/마스터-디테일 Command가 공유하는
 * 공용 Value Object. {@code screenSpecificationId}가 {@code designReferenceId}보다 우선한다.
 * 명세서 §8.1, {@code ORT-PRN-009}.
 */
public record DesignContextReference(
        String designReferenceId,
        String screenSpecificationId
) {
    public static DesignContextReference empty() {
        return new DesignContextReference(null, null);
    }
}
