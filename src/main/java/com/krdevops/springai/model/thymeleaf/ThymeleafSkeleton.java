package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/**
 * I-4C 1단계 산출물: 화면 구조만 결정하고 어떤 값도 바인딩하지 않는다.
 * {@code slots}는 {@link SkeletonSlotKind}만으로 구성되므로 이 타입 자체에는 {@code th:field}가
 * 존재할 수 없다 — 완료 게이트 "Skeleton에 임의 th:field가 없음"을 타입 수준에서 보장한다.
 */
public record ThymeleafSkeleton(
        String screenId,
        LegacyScreenRole screenRole,
        String pageTitle,
        String layoutFragmentRef,
        List<SkeletonSlotKind> slots
) {
    public ThymeleafSkeleton {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        if (pageTitle == null || pageTitle.isBlank()) {
            throw new IllegalArgumentException("pageTitle은 필수입니다.");
        }
        if (layoutFragmentRef == null || layoutFragmentRef.isBlank()) {
            throw new IllegalArgumentException("layoutFragmentRef는 필수입니다.");
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("slots는 최소 1개 이상이어야 합니다.");
        }
        slots = List.copyOf(slots);
    }
}
