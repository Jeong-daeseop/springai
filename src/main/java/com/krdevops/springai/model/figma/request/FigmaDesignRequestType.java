package com.krdevops.springai.model.figma.request;

/** 통합계획서 I-6에서 MCP Tool 7종으로 노출될 요청 유형. figma-design-request-v1 schema와 이름을 일치시킨다. */
public enum FigmaDesignRequestType {
    CREATE_DESIGN_FROM_TEXT,
    CREATE_DESIGN_FROM_REFERENCE,
    MODIFY_EXISTING_DESIGN,
    CREATE_DESIGN_FROM_IMAGE,
    CREATE_MULTI_SCREEN_FLOW,
    CREATE_DESIGN_WITH_COMPONENTS,
    CONVERT_PLATFORM
}
