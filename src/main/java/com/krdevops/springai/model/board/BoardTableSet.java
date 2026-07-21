package com.krdevops.springai.model.board;

/** 한 게시판 생성에서 일관되게 사용할 LETTN 계열 테이블 묶음. */
public record BoardTableSet(
        String mainTable,
        String masterTable,
        String useTable,
        String fileTable,
        String fileDetailTable
) {}
