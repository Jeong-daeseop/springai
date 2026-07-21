package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTableSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 명시 테이블을 우선하고, 미지정 시 LETTN 테이블을 사용한다. */
@Service
@RequiredArgsConstructor
public class BoardTableSetResolver {

    private static final String PREFIX = "LETTN";

    private final CrudSchemaQueryService schemaQueryService;

    public BoardTableSet resolve(String database, String main, String master, String use,
                                 String file, String fileDetail) {
        return new BoardTableSet(
                value(main, PREFIX + "BBS"),
                value(master, PREFIX + "BBSMASTER"),
                optional(database, use, PREFIX + "BBSUSE"),
                optional(database, file, PREFIX + "FILE"),
                optional(database, fileDetail, PREFIX + "FILEDETAIL"));
    }

    private String optional(String database, String explicit, String fallback) {
        if (!blank(explicit)) return explicit.trim();
        return schemaQueryService.tableExists(database, fallback) ? fallback : null;
    }

    private String value(String explicit, String fallback) {
        return blank(explicit) ? fallback : explicit.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
