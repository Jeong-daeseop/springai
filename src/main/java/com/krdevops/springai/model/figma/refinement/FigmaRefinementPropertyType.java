package com.krdevops.springai.model.figma.refinement;

/** Patch 값(`before`/`after`)의 타입. `figma-refinement-patch-set-v1.schema.json`과 동일 값을 사용한다. */
public enum FigmaRefinementPropertyType {
    COLOR,
    NUMBER,
    STRING,
    BOOLEAN,
    ENUM
}
