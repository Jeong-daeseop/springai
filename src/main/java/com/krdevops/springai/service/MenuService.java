package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------------------------
    // getMenuStructure
    // -------------------------------------------------------------------------

    public String getMenuStructure(String menuNo) {
        boolean isRoot = "0".equals(menuNo.trim());

        StringBuilder sb = new StringBuilder();
        sb.append("=== 메뉴 구조 (MENU_NO: ").append(menuNo).append(") ===\n\n");

        if (isRoot) {
            // 전체 트리: root(0) 직계 자식부터 재귀 출력
            List<Map<String, Object>> roots = jdbcTemplate.queryForList(
                "SELECT MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR " +
                "FROM COMTNMENUINFO WHERE UPPER_MENU_NO = 0 ORDER BY MENU_ORDR",
                (Object[]) null
            );
            sb.append("[0] root\n");
            for (int i = 0; i < roots.size(); i++) {
                boolean last = (i == roots.size() - 1);
                appendNode(sb, roots.get(i), "  ", last, true);
            }
        } else {
            // 지정 노드 기준 하위 트리
            List<Map<String, Object>> node = jdbcTemplate.queryForList(
                "SELECT MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR " +
                "FROM COMTNMENUINFO WHERE MENU_NO = ?",
                new BigDecimal(menuNo)
            );
            if (node.isEmpty()) {
                return "MENU_NO " + menuNo + " 에 해당하는 메뉴가 없습니다.";
            }
            Map<String, Object> root = node.get(0);
            sb.append(String.format("[%s] %s  (UPPER: %s, 순서: %s)\n",
                root.get("MENU_NO"), root.get("MENU_NM"),
                root.get("UPPER_MENU_NO"), root.get("MENU_ORDR")));

            List<Map<String, Object>> children = getChildren(menuNo);
            for (int i = 0; i < children.size(); i++) {
                boolean last = (i == children.size() - 1);
                appendNode(sb, children.get(i), "  ", last, false);
            }
        }

        // 신규 등록 권장값 계산
        if (!isRoot) {
            appendRecommendation(sb, menuNo);
        }

        return sb.toString();
    }

    private void appendNode(StringBuilder sb, Map<String, Object> row,
                             String indent, boolean isLast, boolean recurse) {
        String connector = isLast ? "└── " : "├── ";
        String progrmFileNm = row.get("PROGRM_FILE_NM") != null
            ? " → " + row.get("PROGRM_FILE_NM") : "";
        sb.append(String.format("%s%s[%s] %s%s\n",
            indent, connector,
            row.get("MENU_NO"), row.get("MENU_NM"), progrmFileNm));

        if (recurse) {
            String childIndent = indent + (isLast ? "    " : "│   ");
            String menuNo = row.get("MENU_NO").toString();
            List<Map<String, Object>> children = getChildren(menuNo);
            for (int i = 0; i < children.size(); i++) {
                boolean last = (i == children.size() - 1);
                appendNode(sb, children.get(i), childIndent, last, true);
            }
        }
    }

    private List<Map<String, Object>> getChildren(String upperMenuNo) {
        return jdbcTemplate.queryForList(
            "SELECT MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR " +
            "FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ? ORDER BY MENU_ORDR",
            new BigDecimal(upperMenuNo)
        );
    }

    private void appendRecommendation(StringBuilder sb, String upperMenuNo) {
        BigDecimal maxMenuNo = jdbcTemplate.queryForObject(
            "SELECT MAX(MENU_NO) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
            BigDecimal.class, new BigDecimal(upperMenuNo)
        );
        BigDecimal maxOrdr = jdbcTemplate.queryForObject(
            "SELECT MAX(MENU_ORDR) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
            BigDecimal.class, new BigDecimal(upperMenuNo)
        );

        BigDecimal base = new BigDecimal(upperMenuNo);
        BigDecimal nextMenuNo = (maxMenuNo != null)
            ? maxMenuNo.add(BigDecimal.valueOf(10000))
            : base.add(BigDecimal.valueOf(10000));
        int nextOrdr = (maxOrdr != null) ? maxOrdr.intValue() + 1 : 1;

        sb.append("\n[신규 등록 시 권장값]\n");
        sb.append("  MENU_NO   : ").append(nextMenuNo).append("\n");
        sb.append("  MENU_ORDR : ").append(nextOrdr).append("\n");
        sb.append("\n[다음 단계]\n");
        sb.append("  generateMenuInsertSql(\"").append(upperMenuNo)
          .append("\", \"/도메인/기능\", \"메뉴명\", \"ProgrmFileNm\")\n");
    }

    // -------------------------------------------------------------------------
    // generateMenuInsertSql
    // -------------------------------------------------------------------------

    public String generateMenuInsertSql(String upperMenuNo, String urlPrefix,
                                         String menuNm, String progrmFileNm) {
        // MENU_NO 자동 계산
        BigDecimal maxMenuNo = jdbcTemplate.queryForObject(
            "SELECT MAX(MENU_NO) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
            BigDecimal.class, new BigDecimal(upperMenuNo)
        );
        BigDecimal base = new BigDecimal(upperMenuNo);
        BigDecimal nextMenuNo = (maxMenuNo != null)
            ? maxMenuNo.add(BigDecimal.valueOf(10000))
            : base.add(BigDecimal.valueOf(10000));

        // MENU_ORDR 자동 계산
        BigDecimal maxOrdr = jdbcTemplate.queryForObject(
            "SELECT MAX(MENU_ORDR) FROM COMTNMENUINFO WHERE UPPER_MENU_NO = ?",
            BigDecimal.class, new BigDecimal(upperMenuNo)
        );
        int nextOrdr = (maxOrdr != null) ? maxOrdr.intValue() + 1 : 1;

        // urlPrefix 정규화: 끝 슬래시 제거
        String prefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        // 저장경로: 끝 슬래시 보장
        String storePath = prefix.substring(prefix.lastIndexOf("/") + 1).isEmpty()
            ? prefix + "/" : prefix + "/";
        // URL
        String url = prefix + "/" + progrmFileNm + ".do";
        // 저장경로 (슬래시로 끝나는 디렉터리)
        String stre = prefix + "/";

        StringBuilder sb = new StringBuilder();
        sb.append("=== 메뉴·프로그램 등록 SQL ===\n\n");

        sb.append("-- Step 1. 프로그램 등록\n");
        sb.append("INSERT INTO COMTNPROGRMLIST (\n");
        sb.append("    PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, URL\n");
        sb.append(") VALUES (\n");
        sb.append("    '").append(progrmFileNm).append("',\n");
        sb.append("    '").append(stre).append("',\n");
        sb.append("    '").append(menuNm).append("',\n");
        sb.append("    '").append(url).append("'\n");
        sb.append(");\n\n");

        sb.append("-- Step 2. 메뉴 등록\n");
        sb.append("INSERT INTO COMTNMENUINFO (\n");
        sb.append("    MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR\n");
        sb.append(") VALUES (\n");
        sb.append("    ").append(nextMenuNo).append(",\n");
        sb.append("    ").append(upperMenuNo).append(",\n");
        sb.append("    '").append(menuNm).append("',\n");
        sb.append("    '").append(progrmFileNm).append("',\n");
        sb.append("    ").append(nextOrdr).append("\n");
        sb.append(");\n\n");

        sb.append("※ SQL 실행 후 Spring Security 캐시 갱신 또는 서버 재기동이 필요합니다.\n");

        log.info("generateMenuInsertSql: upperMenuNo={}, menuNo={}, ordr={}", upperMenuNo, nextMenuNo, nextOrdr);
        return sb.toString();
    }
}
