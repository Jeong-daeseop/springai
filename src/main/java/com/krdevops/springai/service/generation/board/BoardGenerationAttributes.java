package com.krdevops.springai.service.generation.board;

/** Board 전용 Processor가 GenerationContext에서 읽는 속성 키. */
public final class BoardGenerationAttributes {

    public static final String MODEL = "board.model";
    public static final String VIEW_TYPE = "board.viewType";

    private BoardGenerationAttributes() {
    }
}
