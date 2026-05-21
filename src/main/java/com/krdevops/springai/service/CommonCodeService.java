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
public class CommonCodeService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 코드ID로 상세 코드 목록 조회 (COMTCCMMNDETAILCODE)
     * 예: getCommonCode("COM034") → [A:재직, B:휴직, C:퇴직]
     */
    public String getCommonCode(String codeId) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT d.CODE, d.CODE_NM, d.CODE_DC, d.USE_AT, " +
            "       c.CODE_ID_NM " +
            "FROM COMTCCMMNDETAILCODE d " +
            "JOIN COMTCCMMNCODE c ON d.CODE_ID = c.CODE_ID " +
            "WHERE d.CODE_ID = ? " +
            "ORDER BY d.CODE",
            codeId
        );

        if (list.isEmpty()) {
            return "코드ID '" + codeId + "' 에 해당하는 공통 코드가 없습니다.";
        }

        String codeIdNm = (String) list.get(0).get("CODE_ID_NM");
        StringBuilder sb = new StringBuilder();
        sb.append("=== 공통 코드: ").append(codeId).append(" (").append(codeIdNm).append(") ===\n");
        sb.append(String.format("%-15s %-30s %-6s %s\n", "CODE", "CODE_NM", "USE_AT", "CODE_DC"));
        sb.append("-".repeat(80)).append("\n");

        for (Map<String, Object> row : list) {
            sb.append(String.format("%-15s %-30s %-6s %s\n",
                row.get("CODE"),
                row.get("CODE_NM"),
                row.get("USE_AT"),
                row.getOrDefault("CODE_DC", "")
            ));
        }

        sb.append("\n[JSP select box 생성용]\n");
        sb.append("<select name=\"").append(toCamelCase(codeId)).append("\">\n");
        sb.append("    <option value=\"\">-- 선택 --</option>\n");
        for (Map<String, Object> row : list) {
            if ("Y".equals(row.get("USE_AT"))) {
                sb.append("    <option value=\"").append(row.get("CODE")).append("\">")
                  .append(row.get("CODE_NM")).append("</option>\n");
            }
        }
        sb.append("</select>");

        log.info("공통 코드 조회: codeId={}, count={}", codeId, list.size());
        return sb.toString();
    }

    /**
     * 키워드로 코드ID 목록 검색 (COMTCCMMNCODE)
     * 예: searchCommonCode("직원") → COM034(직원상태), ...
     */
    public String searchCommonCode(String keyword) {
        List<Map<String, Object>> list;

        if (keyword == null || keyword.isBlank()) {
            list = jdbcTemplate.queryForList(
                "SELECT CODE_ID, CODE_ID_NM, CODE_ID_DC, USE_AT " +
                "FROM COMTCCMMNCODE " +
                "ORDER BY CODE_ID LIMIT 50"
            );
        } else {
            String like = "%" + keyword + "%";
            list = jdbcTemplate.queryForList(
                "SELECT CODE_ID, CODE_ID_NM, CODE_ID_DC, USE_AT " +
                "FROM COMTCCMMNCODE " +
                "WHERE CODE_ID_NM LIKE ? OR CODE_ID_DC LIKE ? OR CODE_ID LIKE ? " +
                "ORDER BY CODE_ID LIMIT 50",
                like, like, like
            );
        }

        if (list.isEmpty()) {
            return "'" + keyword + "' 에 해당하는 공통 코드 그룹이 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 공통 코드 그룹 목록 (").append(list.size()).append("건) ===\n");
        sb.append(String.format("%-8s %-30s %-6s %s\n", "CODE_ID", "CODE_ID_NM", "USE_AT", "CODE_ID_DC"));
        sb.append("-".repeat(80)).append("\n");

        for (Map<String, Object> row : list) {
            sb.append(String.format("%-8s %-30s %-6s %s\n",
                row.get("CODE_ID"),
                row.get("CODE_ID_NM"),
                row.get("USE_AT"),
                row.getOrDefault("CODE_ID_DC", "")
            ));
        }

        sb.append("\n상세 코드 조회: getCommonCode(codeId) 를 사용하세요.");
        return sb.toString();
    }

    private String toCamelCase(String codeId) {
        return codeId == null ? "" : codeId.substring(0, 1).toLowerCase() + codeId.substring(1);
    }
}
