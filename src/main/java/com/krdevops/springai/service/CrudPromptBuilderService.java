package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrudPromptBuilderService {

    private final JdbcTemplate jdbcTemplate;
    private final CommonCodeService commonCodeService;
    private final EgovPromptBuilder promptBuilder;

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath) {
        // 1. 컬럼 정보 조회
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        if (columns.isEmpty()) {
            return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
        }

        // 2. PK 탐지
        Map<String, Object> pkCol = columns.stream()
            .filter(c -> "PRI".equals(c.get("COLUMN_KEY")))
            .findFirst()
            .orElse(columns.get(0));

        String pkColumn  = (String) pkCol.get("COLUMN_NAME");
        String pkField   = toCamelCase(pkColumn);
        String pkType    = toJavaType((String) pkCol.get("DATA_TYPE"));

        // 3. 플레이스홀더 값 생성
        String domainLc    = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        String domainKr    = extractKoreanName(tableName);
        String urlPrefix   = "/" + packageName.replace("egovframework.let.", "").replace(".", "/") + "/" + domainLc;
        String date        = LocalDate.now().toString();

        String voFields        = buildVoFields(columns);
        String mapperColumns   = buildMapperColumns(columns);
        String insertColumns   = buildInsertColumns(columns);
        String insertValues    = buildInsertValues(columns);
        String updateSet       = buildUpdateSet(columns, pkColumn);
        String resultMapFields = buildResultMapFields(columns, packageName, domain);
        String jspListTh       = buildJspListTh(columns);
        String jspListTd       = buildJspListTd(columns, pkField);
        String jspDetailRows   = buildJspDetailRows(columns);
        String jspFormInputs   = buildJspFormInputs(columns, pkColumn);

        // 4. 공통 코드 컬럼 탐지 및 조회
        String commonCodeSection = buildCommonCodeSection(columns);

        // 5. 통합 프롬프트 조립
        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 5.x CRUD 전체 소스 생성 지시 ===\n\n");

        sb.append("[테이블 정보]\n");
        sb.append("  DB        : ").append(database).append("\n");
        sb.append("  테이블    : ").append(tableName).append("\n");
        sb.append("  PK 컬럼   : ").append(pkColumn).append(" (").append(pkType).append(")\n\n");

        sb.append("[플레이스홀더 치환 규칙]\n");
        sb.append("  {{PACKAGE}}           = ").append(packageName).append("\n");
        sb.append("  {{DOMAIN}}            = ").append(domain).append("\n");
        sb.append("  {{DOMAIN_LC}}         = ").append(domainLc).append("\n");
        sb.append("  {{DOMAIN_KR}}         = ").append(domainKr).append("\n");
        sb.append("  {{TABLE_NAME}}        = ").append(tableName).append("\n");
        sb.append("  {{PK_FIELD}}          = ").append(pkField).append("\n");
        sb.append("  {{PK_COLUMN}}         = ").append(pkColumn).append("\n");
        sb.append("  {{PK_TYPE}}           = ").append(pkType).append("\n");
        sb.append("  {{URL_PREFIX}}        = ").append(urlPrefix).append("\n");
        sb.append("  {{DATE}}              = ").append(date).append("\n\n");

        sb.append("  {{VO_FIELDS}}\n").append(voFields).append("\n");
        sb.append("  {{MAPPER_COLUMNS}}    = ").append(mapperColumns).append("\n");
        sb.append("  {{INSERT_COLUMNS}}    = ").append(insertColumns).append("\n");
        sb.append("  {{INSERT_VALUES}}     = ").append(insertValues).append("\n");
        sb.append("  {{UPDATE_SET}}\n").append(updateSet).append("\n");
        sb.append("  {{RESULT_MAP_FIELDS}}\n").append(resultMapFields).append("\n");
        sb.append("  {{JSP_LIST_TH}}\n").append(jspListTh).append("\n");
        sb.append("  {{JSP_LIST_TD}}\n").append(jspListTd).append("\n");
        sb.append("  {{JSP_DETAIL_ROWS}}\n").append(jspDetailRows).append("\n");
        sb.append("  {{JSP_FORM_INPUTS}}\n").append(jspFormInputs).append("\n");

        if (!commonCodeSection.isEmpty()) {
            sb.append("[공통 코드 참조]\n").append(commonCodeSection).append("\n");
        }

        sb.append(promptBuilder.crudConstraints());

        sb.append("[생성 지시]\n");
        sb.append("generateSource(layer, valuesJson)로 소스를 생성하고 saveGeneratedCode()로 저장하세요.\n");
        sb.append("valuesJson은 위 [플레이스홀더 치환 규칙]의 키-값을 JSON으로 전달하면 됩니다.\n");
        sb.append("출력 경로: ").append(outputPath).append("\n\n");

        String[][] layers = {
            {"vo",          domain + "VO.java"},
            {"mapper",      domain + "Mapper.java"},
            {"mapperXml",   domain + "Mapper.xml"},
            {"service",     domain + "Service.java"},
            {"serviceImpl", "Egov" + domain + "ServiceImpl.java"},
            {"controller",  "Egov" + domain + "Controller.java"},
            {"jspList",     "Egov" + domain + "List.jsp"},
            {"jspDetail",   "Egov" + domain + "Detail.jsp"},
            {"jspRegist",   "Egov" + domain + "Regist.jsp"},
            {"jspUpdt",     "Egov" + domain + "Updt.jsp"},
        };
        for (int i = 0; i < layers.length; i++) {
            sb.append(String.format("  Step %2d: getCodeTemplate(\"%s\") → %s\n",
                i + 1, layers[i][0], layers[i][1]));
        }

        sb.append("\n").append(promptBuilder.postGeneration(outputPath, tableName, domain, packageName, domainLc, "10개 파일"));

        log.info("CRUD 프롬프트 빌드 완료: table={}, domain={}", tableName, domain);
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchColumns(String database, String tableName) {
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

    private String buildVoFields(List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String field   = toCamelCase((String) col.get("COLUMN_NAME"));
            String javaType = toJavaType((String) col.get("DATA_TYPE"));
            String comment = col.get("COLUMN_COMMENT") != null ? (String) col.get("COLUMN_COMMENT") : "";
            if (!comment.isEmpty()) sb.append("    // ").append(comment).append("\n");
            sb.append("    private ").append(javaType).append(" ").append(field).append(";\n");
        }
        return sb.toString();
    }

    private String buildMapperColumns(List<Map<String, Object>> columns) {
        List<String> cols = new ArrayList<>();
        for (Map<String, Object> col : columns) cols.add((String) col.get("COLUMN_NAME"));
        return String.join(", ", cols);
    }

    private String buildInsertColumns(List<Map<String, Object>> columns) {
        List<String> cols = new ArrayList<>();
        for (Map<String, Object> col : columns) cols.add("            " + col.get("COLUMN_NAME"));
        return String.join(",\n", cols);
    }

    private String buildInsertValues(List<Map<String, Object>> columns) {
        List<String> vals = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            vals.add("            #{" + toCamelCase((String) col.get("COLUMN_NAME")) + "}");
        }
        return String.join(",\n", vals);
    }

    private String buildUpdateSet(List<Map<String, Object>> columns, String pkColumn) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String colName = (String) col.get("COLUMN_NAME");
            if (colName.equals(pkColumn)) continue;
            sb.append("            ").append(colName).append(" = #{")
              .append(toCamelCase(colName)).append("},\n");
        }
        return sb.toString();
    }

    private String buildResultMapFields(List<Map<String, Object>> columns,
                                        String packageName, String domain) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String colName = (String) col.get("COLUMN_NAME");
            String field   = toCamelCase(colName);
            boolean isPk   = "PRI".equals(col.get("COLUMN_KEY"));
            if (isPk) {
                sb.append("        <id     property=\"").append(field)
                  .append("\" column=\"").append(colName).append("\"/>\n");
            } else {
                sb.append("        <result property=\"").append(field)
                  .append("\" column=\"").append(colName).append("\"/>\n");
            }
        }
        return sb.toString();
    }

    private String buildJspListTh(List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String comment = col.get("COLUMN_COMMENT") != null &&
                             !((String) col.get("COLUMN_COMMENT")).isBlank()
                           ? (String) col.get("COLUMN_COMMENT")
                           : toCamelCase((String) col.get("COLUMN_NAME"));
            sb.append("                            <th>").append(comment).append("</th>\n");
        }
        return sb.toString();
    }

    private String buildJspListTd(List<Map<String, Object>> columns, String pkField) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String field = toCamelCase((String) col.get("COLUMN_NAME"));
            sb.append("                                <td>${item.").append(field).append("}</td>\n");
        }
        return sb.toString();
    }

    private String buildJspDetailRows(List<Map<String, Object>> columns) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String field   = toCamelCase((String) col.get("COLUMN_NAME"));
            String comment = col.get("COLUMN_COMMENT") != null &&
                             !((String) col.get("COLUMN_COMMENT")).isBlank()
                           ? (String) col.get("COLUMN_COMMENT") : field;
            sb.append("                        <tr><th>").append(comment)
              .append("</th><td>${result.").append(field).append("}</td></tr>\n");
        }
        return sb.toString();
    }

    private String buildJspFormInputs(List<Map<String, Object>> columns, String pkColumn) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String colName = (String) col.get("COLUMN_NAME");
            String field   = toCamelCase(colName);
            String comment = col.get("COLUMN_COMMENT") != null &&
                             !((String) col.get("COLUMN_COMMENT")).isBlank()
                           ? (String) col.get("COLUMN_COMMENT") : field;
            sb.append("                            <tr><th>").append(comment).append("</th><td>\n");
            sb.append("                                <input type=\"text\" name=\"").append(field)
              .append("\" value=\"${").append(field).append("}\"");
            if (colName.equals(pkColumn)) sb.append(" readonly=\"readonly\"");
            sb.append("/>\n                            </td></tr>\n");
        }
        return sb.toString();
    }

    private String buildCommonCodeSection(List<Map<String, Object>> columns) {
        // COMTCCMMNCODE 전체를 1회만 조회하여 Map으로 캐싱 (N회 쿼리 → 1회)
        Map<String, String> codeIdMap;
        try {
            codeIdMap = jdbcTemplate.queryForList(
                "SELECT CODE_ID, CODE_ID_NM FROM COMTCCMMNCODE"
            ).stream().collect(Collectors.toMap(
                r -> (String) r.get("CODE_ID"),
                r -> r.get("CODE_ID_NM") != null ? (String) r.get("CODE_ID_NM") : "",
                (a, b) -> a
            ));
        } catch (Exception e) {
            log.warn("공통코드 목록 조회 실패: {}", e.getMessage());
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> col : columns) {
            String colName = (String) col.get("COLUMN_NAME");
            if (!colName.endsWith("_CODE") && !colName.endsWith("_CD")) continue;

            String colBase = colName.replace("_CODE", "").replace("_CD", "");
            // 최소 5글자 이상인 경우만 매칭 시도 (오탐 방지)
            if (colBase.length() < 5) continue;

            for (Map.Entry<String, String> entry : codeIdMap.entrySet()) {
                String codeId   = entry.getKey();
                String codeIdNm = entry.getValue();
                // colBase 전체가 codeId에 포함되거나 codeId 전체가 colBase에 포함될 때만 매칭
                if (colBase.equals(codeId)
                        || codeId.contains(colBase)
                        || colBase.contains(codeId)) {
                    try {
                        String codeInfo = commonCodeService.getCommonCode(codeId);
                        if (!codeInfo.contains("없습니다")) {
                            sb.append("  컬럼 ").append(colName).append(" → ")
                              .append(codeId).append(" (").append(codeIdNm).append(")\n");
                            sb.append(codeInfo).append("\n");
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("공통코드 상세 조회 실패: codeId={}", codeId);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String toCamelCase(String columnName) {
        if (columnName == null) return "";
        String[] parts = columnName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private String toJavaType(String dataType) {
        if (dataType == null) return "String";
        return switch (dataType.toLowerCase()) {
            case "int", "tinyint", "smallint", "mediumint" -> "Integer";
            case "bigint"                                   -> "Long";
            case "decimal", "numeric", "float", "double"   -> "java.math.BigDecimal";
            case "datetime", "timestamp"                    -> "String";
            case "date"                                     -> "String";
            case "bit", "boolean"                           -> "Boolean";
            default                                         -> "String";
        };
    }

    private String extractKoreanName(String tableName) {
        // 테이블명에서 도메인 힌트 추출 (기본값 반환)
        return tableName.replace("COMTN", "").replace("COMTS", "").replace("COMTC", "");
    }
}
