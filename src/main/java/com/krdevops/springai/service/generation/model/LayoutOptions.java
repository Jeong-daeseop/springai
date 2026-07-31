package com.krdevops.springai.service.generation.model;

/**
 * Thymeleaf layout 처리 방식과 참조 경로 — CRUD/게시판/마스터-디테일 Command가 공유하는
 * 공용 Value Object. 명세서 §8.1.
 */
public record LayoutOptions(
        String layoutMode,
        String layoutView,
        String breadcrumbView
) {
    public static LayoutOptions empty() {
        return new LayoutOptions(null, null, null);
    }
}
