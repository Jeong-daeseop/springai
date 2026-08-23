package com.krdevops.springai.model.renderer;

/** Renderer가 명시적으로 지원한다고 선언할 수 있는 생성 기능. */
public enum RendererFeature {
    CRUD_LIST,
    CRUD_DETAIL,
    CRUD_CREATE,
    CRUD_UPDATE,
    CRUD_SEARCH,
    COMPOSITE_PRIMARY_KEY,
    SCREEN_FIELD_SUBSET,
    LAYOUT_DECORATION
}
