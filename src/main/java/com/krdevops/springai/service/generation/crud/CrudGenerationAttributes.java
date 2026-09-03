package com.krdevops.springai.service.generation.crud;

/**
 * CRUD 전용 Processor/Renderer가 {@code GenerationContext.attributes}에서 꺼내 쓰는 키.
 * 공용 Processor는 이 키를 참조하지 않는다.
 */
public final class CrudGenerationAttributes {

    public static final String MODEL = "crud.model";
    public static final String VIEW_TYPE = "crud.viewType";
    public static final String LAYOUT_MODE = "crud.layoutMode";
    public static final String LAYOUT_REFERENCE = "crud.layoutReference";
    public static final String DESIGN_SYSTEM_PROFILE_ID = "crud.designSystemProfileId";

    private CrudGenerationAttributes() {
    }
}
