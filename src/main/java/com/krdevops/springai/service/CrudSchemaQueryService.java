package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * DB 테이블 컬럼 메타데이터 조회 서비스.
 * CrudPromptBuilderService.fetchColumns() 의 JdbcTemplate 조회 책임을 분리하여
 * CrudModelFactory 와 CrudPromptBuilderService 양쪽에서 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class CrudSchemaQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * INFORMATION_SCHEMA 에서 컬럼 메타데이터를 조회한다.
     *
     * <p>반환 Map 의 키:</p>
     * <ul>
     *   <li>COLUMN_NAME — DB 컬럼명</li>
     *   <li>DATA_TYPE — MySQL 타입 (소문자, 예: varchar, int)</li>
     *   <li>CHARACTER_MAXIMUM_LENGTH — VARCHAR 최대 길이 (null 가능)</li>
     *   <li>IS_NULLABLE — 'YES' | 'NO'</li>
     *   <li>COLUMN_COMMENT — 컬럼 코멘트</li>
     *   <li>COLUMN_KEY — 'PRI' (PK) | '' (일반 컬럼)</li>
     * </ul>
     *
     * @param database  DB 스키마명
     * @param tableName 테이블명
     * @return 컬럼 목록 (ORDINAL_POSITION 순), 테이블 미존재 시 빈 리스트
     */
    public List<Map<String, Object>> fetchColumns(String database, String tableName) {
        return jdbcTemplate.queryForList(
            "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.CHARACTER_MAXIMUM_LENGTH, " +
            "  c.IS_NULLABLE, c.COLUMN_COMMENT, " +
            "  CASE WHEN kcu.COLUMN_NAME IS NOT NULL THEN 'PRI' ELSE '' END AS COLUMN_KEY " +
            "FROM INFORMATION_SCHEMA.COLUMNS c " +
            "LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
            "  ON kcu.TABLE_SCHEMA = c.TABLE_SCHEMA AND kcu.TABLE_NAME = c.TABLE_NAME " +
            "  AND kcu.COLUMN_NAME = c.COLUMN_NAME AND kcu.CONSTRAINT_NAME = 'PRIMARY' " +
            "WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ? " +
            "ORDER BY c.ORDINAL_POSITION",
            database, tableName
        );
    }

    public boolean tableExists(String database, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Integer.class, database, tableName);
        return count != null && count > 0;
    }
}
