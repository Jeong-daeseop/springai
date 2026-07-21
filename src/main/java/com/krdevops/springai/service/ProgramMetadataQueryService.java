package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code LETTN} 프로그램·메뉴 테이블을 읽기 전용으로 조회하는 공용 헬퍼.
 *
 * <p>{@link BoardProgramMetadataService}(게시판)와 {@link CrudProgramMetadataService}(범용 CRUD)가
 * 동일한 테이블 탐지·조회·토큰 정규화 로직을 공유한다. 이 서비스 자체는 DB에 값을 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ProgramMetadataQueryService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final JdbcTemplate jdbcTemplate;
    private final CrudSchemaQueryService schemaQueryService;

    /** LETTNPROGRMLIST 한 행(LETTNMENUINFO와 조인 시 상위 메뉴명 포함). */
    public record Candidate(String fileName, String storePath, String koreanName,
                             String url, String upperMenuName) {
        public String key() {
            return String.valueOf(fileName) + '|' + String.valueOf(url);
        }
    }

    public String firstExistingProgramTable(String database) {
        return firstExisting(database, "LETTNPROGRMLIST");
    }

    public String firstExistingMenuTable(String database) {
        return firstExisting(database, "LETTNMENUINFO");
    }

    /** 현재 연결된 스키마 기준으로 프로그램 테이블(LETTN 우선)을 탐지한다. 없으면 LETTNPROGRMLIST로 기본 처리한다. */
    public String firstExistingProgramTable() {
        String table = firstExistingProgramTable(currentSchema());
        return table != null ? table : "LETTNPROGRMLIST";
    }

    /** 현재 연결된 스키마 기준으로 메뉴 테이블(LETTN 우선)을 탐지한다. 없으면 LETTNMENUINFO로 기본 처리한다. */
    public String firstExistingMenuTable() {
        String table = firstExistingMenuTable(currentSchema());
        return table != null ? table : "LETTNMENUINFO";
    }

    private String currentSchema() {
        return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
    }

    public List<Candidate> loadCandidates(String database, String programTable, String menuTable) {
        validateIdentifier(database, "database");
        validateIdentifier(programTable, "programTable");
        String program = qualified(database, programTable);
        if (menuTable == null) {
            return jdbcTemplate.query("SELECT PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, URL "
                            + "FROM " + program,
                    (rs, rowNum) -> new Candidate(rs.getString("PROGRM_FILE_NM"),
                            rs.getString("PROGRM_STRE_PATH"), rs.getString("PROGRM_KOREAN_NM"),
                            rs.getString("URL"), null));
        }
        validateIdentifier(menuTable, "menuTable");
        String menu = qualified(database, menuTable);
        String sql = "SELECT p.PROGRM_FILE_NM, p.PROGRM_STRE_PATH, p.PROGRM_KOREAN_NM, p.URL, "
                + "parent.MENU_NM AS UPPER_MENU_NM FROM " + program + " p "
                + "LEFT JOIN " + menu + " child ON child.PROGRM_FILE_NM = p.PROGRM_FILE_NM "
                + "LEFT JOIN " + menu + " parent ON parent.MENU_NO = child.UPPER_MENU_NO";
        List<Candidate> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new Candidate(
                rs.getString("PROGRM_FILE_NM"), rs.getString("PROGRM_STRE_PATH"),
                rs.getString("PROGRM_KOREAN_NM"), rs.getString("URL"),
                rs.getString("UPPER_MENU_NM")));
        Map<String, Candidate> unique = new LinkedHashMap<>();
        rows.forEach(row -> unique.putIfAbsent(row.key(), row));
        return new ArrayList<>(unique.values());
    }

    /** 마스터 테이블에 지정 PK 문자열 값(예: bbsId)이 존재하는 행이 있는지 카운트한다. */
    public Integer queryForBbsIdCount(String database, String masterTable, String bbsId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + qualified(database, masterTable) + " WHERE BBS_ID = ?",
                Integer.class, bbsId);
    }

    public String normalizeToken(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9가-힣]", "")
                .toLowerCase(Locale.ROOT);
    }

    public String normalizeProgramFileName(String value) {
        String normalized = normalizeToken(value);
        return normalized.startsWith("egov") ? normalized.substring(4) : normalized;
    }

    private String firstExisting(String database, String... tables) {
        for (String table : tables) {
            if (schemaQueryService.tableExists(database, table)) return table;
        }
        return null;
    }

    private String qualified(String database, String table) {
        validateIdentifier(database, "database");
        validateIdentifier(table, "table");
        return "`" + database + "`.`" + table + "`";
    }

    public void validateIdentifier(String value, String label) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " 식별자가 올바르지 않습니다: " + value);
        }
    }
}
