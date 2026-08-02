package com.krdevops.springai.model.thymeleaf;

/**
 * {@link ThymeleafSkeleton}이 가질 수 있는 구조적 구획. {@code screenRole}만으로 결정되며
 * 값 자체에는 어떤 필드·바인딩 정보도 담기지 않는다("Skeleton에 임의 th:field가 없음").
 */
public enum SkeletonSlotKind {
    SEARCH_FORM,
    DATA_TABLE,
    FORM_FIELDS,
    DISPLAY_FIELDS,
    ACTION_BAR
}
