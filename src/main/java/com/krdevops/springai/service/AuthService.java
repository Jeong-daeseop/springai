package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // getProgramList
    // -------------------------------------------------------------------------

    public String getProgramList(String keyword) {
        List<Map<String, Object>> list;
        String like = "%" + keyword + "%";

        if (keyword == null || keyword.isBlank()) {
            list = jdbcTemplate.queryForList(
                "SELECT PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, URL " +
                "FROM COMTNPROGRMLIST ORDER BY PROGRM_FILE_NM LIMIT 50"
            );
        } else {
            list = jdbcTemplate.queryForList(
                "SELECT PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, URL " +
                "FROM COMTNPROGRMLIST " +
                "WHERE PROGRM_FILE_NM LIKE ? OR PROGRM_KOREAN_NM LIKE ? OR URL LIKE ? " +
                "ORDER BY PROGRM_FILE_NM LIMIT 50",
                like, like, like
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 프로그램 목록 — 검색어: ").append(keyword).append(" ===\n\n");

        if (list.isEmpty()) {
            sb.append("검색 결과 없음 (0건)\n\n");
            sb.append("→ 신규 등록 가능합니다.\n");
            sb.append("  generateMenuInsertSql() 로 COMTNPROGRMLIST INSERT SQL을 생성하세요.\n");
            return sb.toString();
        }

        sb.append(String.format("%-30s %-25s %-15s %s\n",
            "PROGRM_FILE_NM", "저장경로", "한국어명", "URL"));
        sb.append("-".repeat(100)).append("\n");

        for (Map<String, Object> row : list) {
            sb.append(String.format("%-30s %-25s %-15s %s\n",
                row.get("PROGRM_FILE_NM"),
                row.get("PROGRM_STRE_PATH"),
                row.getOrDefault("PROGRM_KOREAN_NM", ""),
                row.get("URL")
            ));
        }

        sb.append("\n총 ").append(list.size()).append("건");
        if (list.size() >= 50) sb.append(" (최대 50건 표시)");
        sb.append("\n\n");
        sb.append("→ 이미 등록된 프로그램이 있습니다. 중복 등록 전 확인하세요.\n");

        log.info("getProgramList: keyword={}, count={}", keyword, list.size());
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // generateAuthInsertSql
    // -------------------------------------------------------------------------

    public String generateAuthInsertSql(String urlPrefix, String programNm, String domain) {
        // ROLE_CODE 자동 계산: web-NNNNNN 형식
        Integer maxRoleNum = jdbcTemplate.queryForObject(
            "SELECT MAX(CAST(SUBSTRING(ROLE_CODE, 5) AS UNSIGNED)) " +
            "FROM COMTNROLEINFO WHERE ROLE_CODE LIKE 'web-%'",
            Integer.class
        );
        int nextRoleNum = (maxRoleNum != null) ? maxRoleNum + 1 : 1;
        String roleCode = String.format("web-%06d", nextRoleNum);

        // URL 패턴 생성
        String prefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        String rolePttrn = "\\\\A" + prefix + "/.*\\\\.do.*\\\\Z";

        StringBuilder sb = new StringBuilder();
        sb.append("=== 권한·롤 등록 SQL ===\n\n");

        sb.append("-- Step 1. 롤 등록 (URL 패턴 기반 접근 제어)\n");
        sb.append("INSERT INTO COMTNROLEINFO (\n");
        sb.append("    ROLE_CODE, ROLE_NM, ROLE_PTTRN, ROLE_DC, ROLE_TY, ROLE_SORT, ROLE_CREAT_DE\n");
        sb.append(") VALUES (\n");
        sb.append("    '").append(roleCode).append("',\n");
        sb.append("    '").append(programNm).append("',\n");
        sb.append("    '").append(rolePttrn).append("',\n");
        sb.append("    '").append(domain).append(" 도메인 접근 롤',\n");
        sb.append("    'url',\n");
        sb.append("    '").append(nextRoleNum).append("',\n");
        sb.append("    NOW()\n");
        sb.append(");\n\n");

        sb.append("-- Step 2. ROLE_ADMIN 권한에 롤 연결\n");
        sb.append("INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)\n");
        sb.append("VALUES ('ROLE_ADMIN', '").append(roleCode).append("', NOW());\n\n");

        sb.append("-- Step 3. 추가 권한 그룹 연결 (필요 시 AUTHOR_CODE 변경 후 실행)\n");
        sb.append("-- INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)\n");
        sb.append("-- VALUES ('ROLE_USER', '").append(roleCode).append("', NOW());\n\n");

        sb.append("※ ROLE_CODE(").append(roleCode).append(")는 기존 최대값 기준 자동 계산된 값입니다.\n");
        sb.append("※ 실제 적용을 위해서는 Spring Security 재기동 또는 캐시 갱신이 필요합니다.\n");

        log.info("generateAuthInsertSql: roleCode={}, urlPrefix={}", roleCode, urlPrefix);
        return sb.toString();
    }
}
