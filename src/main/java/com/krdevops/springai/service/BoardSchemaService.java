package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시판(BBS) 업무 단위 스키마 조회 Service.
 *
 * <p>게시글/마스터/사용권한/첨부파일 테이블을 한 번에 조회하여
 * 논리 키("main", "master", "use", "file", "fileDetail")로 묶어 반환한다.
 */
@Service
@RequiredArgsConstructor
public class BoardSchemaService {

    private final CrudSchemaQueryService schemaQueryService;

    /**
     * @throws IllegalArgumentException main 또는 master 테이블 컬럼이 비어 있는 경우
     */
    public Map<String, List<Map<String, Object>>> fetchBoardSchemas(
            String database,
            String mainTable, String masterTable,
            String useTable, String fileTable, String fileDetailTable) {

        var result = new LinkedHashMap<String, List<Map<String, Object>>>();

        // 필수 테이블
        List<Map<String, Object>> mainCols = schemaQueryService.fetchColumns(database, mainTable);
        if (mainCols.isEmpty()) throw new IllegalArgumentException("게시판 테이블 없음: " + mainTable);
        result.put("main", mainCols);

        List<Map<String, Object>> masterCols = schemaQueryService.fetchColumns(database, masterTable);
        if (masterCols.isEmpty()) throw new IllegalArgumentException("게시판 마스터 테이블 없음: " + masterTable);
        result.put("master", masterCols);

        // 옵션 테이블
        if (useTable != null) {
            result.put("use", schemaQueryService.fetchColumns(database, useTable));
        }
        if (fileTable != null) {
            result.put("file", schemaQueryService.fetchColumns(database, fileTable));
        }
        if (fileDetailTable != null) {
            result.put("fileDetail", schemaQueryService.fetchColumns(database, fileDetailTable));
        }
        return result;
    }
}
