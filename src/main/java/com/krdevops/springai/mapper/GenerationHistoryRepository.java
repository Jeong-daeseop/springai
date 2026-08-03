package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class GenerationHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final LegacyRepositoryDdlProperties ddlProperties;

    private final RowMapper<Map<String, Object>> rowMapper = (rs, rowNum) -> Map.of(
        "id",            rs.getLong("ID"),
        "tableName",     rs.getString("TABLE_NAME"),
        "domain",        rs.getString("DOMAIN"),
        "packageName",   rs.getString("PACKAGE_NAME"),
        "outputPath",    rs.getString("OUTPUT_PATH"),
        "generatedFiles", rs.getString("GENERATED_FILES"),
        "createdAt",     rs.getString("CREATED_AT")
    );

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_GENERATION_HISTORY (
                ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
                TABLE_NAME     VARCHAR(100) NOT NULL,
                DOMAIN         VARCHAR(100) NOT NULL,
                PACKAGE_NAME   VARCHAR(200),
                OUTPUT_PATH    VARCHAR(500),
                GENERATED_FILES TEXT,
                CREATED_AT     DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """);
        log.info("AI_GENERATION_HISTORY 테이블 초기화 완료");
    }

    public long insert(String tableName, String domain, String packageName,
                       String outputPath, String generatedFiles) {
        jdbcTemplate.update(
            "INSERT INTO AI_GENERATION_HISTORY (TABLE_NAME, DOMAIN, PACKAGE_NAME, OUTPUT_PATH, GENERATED_FILES, CREATED_AT) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            tableName, domain, packageName, outputPath, generatedFiles, LocalDateTime.now()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<Map<String, Object>> selectByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query(
                "SELECT ID, TABLE_NAME, DOMAIN, PACKAGE_NAME, OUTPUT_PATH, GENERATED_FILES, " +
                "DATE_FORMAT(CREATED_AT, '%Y-%m-%d %H:%i:%s') AS CREATED_AT " +
                "FROM AI_GENERATION_HISTORY ORDER BY CREATED_AT DESC LIMIT 20",
                rowMapper
            );
        }
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(
            "SELECT ID, TABLE_NAME, DOMAIN, PACKAGE_NAME, OUTPUT_PATH, GENERATED_FILES, " +
            "DATE_FORMAT(CREATED_AT, '%Y-%m-%d %H:%i:%s') AS CREATED_AT " +
            "FROM AI_GENERATION_HISTORY " +
            "WHERE TABLE_NAME LIKE ? OR DOMAIN LIKE ? OR PACKAGE_NAME LIKE ? " +
            "ORDER BY CREATED_AT DESC LIMIT 20",
            rowMapper, like, like, like
        );
    }

    public Map<String, Object> selectById(long id) {
        List<Map<String, Object>> list = jdbcTemplate.query(
            "SELECT ID, TABLE_NAME, DOMAIN, PACKAGE_NAME, OUTPUT_PATH, GENERATED_FILES, " +
            "DATE_FORMAT(CREATED_AT, '%Y-%m-%d %H:%i:%s') AS CREATED_AT " +
            "FROM AI_GENERATION_HISTORY WHERE ID = ?",
            rowMapper, id
        );
        return list.isEmpty() ? null : list.get(0);
    }
}
