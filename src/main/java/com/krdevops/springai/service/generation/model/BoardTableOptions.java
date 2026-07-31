package com.krdevops.springai.service.generation.model;

/** 게시판 5테이블 명시 옵션. 미지정 필드는 {@code BoardTableSetResolver}가 기본값을 적용한다. */
public record BoardTableOptions(
        String mainTable,
        String masterTable,
        String useTable,
        String fileTable,
        String fileDetailTable
) {
    public static BoardTableOptions empty() {
        return new BoardTableOptions(null, null, null, null, null);
    }
}
