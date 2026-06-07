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

    /**
     * 플레이스홀더 치환에 필요한 모든 값을 담는 레코드.
     * auto 모드에서 CodeService.generateSource() 호출 시 재사용합니다.
     */
    public record PlaceholderValues(
        String packageName,
        String domain,
        String domainLc,
        String domainKr,
        String tableName,
        String pkField,
        String pkColumn,
        String pkType,
        String urlPrefix,
        String date,
        String validationImport,
        String voFields,
        String mapperColumns,
        String insertColumns,
        String insertValues,
        String updateSet,
        String resultMapFields,
        String jspListTh,
        String jspListTd,
        String jspDetailRows,
        String jspFormInputs
    ) {
        /** Map 형태로 변환 — CodeService.generateSource(layer, values) 에 그대로 전달합니다. */
        public Map<String, String> toMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("PACKAGE",            packageName);
            map.put("DOMAIN",             domain);
            map.put("DOMAIN_LC",          domainLc);
            map.put("DOMAIN_KR",          domainKr);
            map.put("TABLE_NAME",         tableName);
            map.put("PK_FIELD",           pkField);
            map.put("PK_COLUMN",          pkColumn);
            map.put("PK_TYPE",            pkType);
            map.put("URL_PREFIX",         urlPrefix);
            map.put("DATE",               date);
            map.put("VALIDATION_IMPORT",  validationImport);
            map.put("VO_FIELDS",          voFields);
            map.put("MAPPER_COLUMNS",     mapperColumns);
            map.put("INSERT_COLUMNS",     insertColumns);
            map.put("INSERT_VALUES",      insertValues);
            map.put("UPDATE_SET",         updateSet);
            map.put("RESULT_MAP_FIELDS",  resultMapFields);
            map.put("JSP_LIST_TH",        jspListTh);
            map.put("JSP_LIST_TD",        jspListTd);
            map.put("JSP_DETAIL_ROWS",    jspDetailRows);
            map.put("JSP_FORM_INPUTS",    jspFormInputs);
            return map;
        }
    }

    /**
     * DB 스키마 정보를 조회하여 플레이스홀더 값을 계산합니다.
     * buildFullCrudPrompt()와 auto 모드 오케스트레이션이 공통으로 사용합니다.
     *
     * @return 모든 플레이스홀더가 채워진 PlaceholderValues, 테이블 미존재 시 null
     */
    public PlaceholderValues buildPlaceholderValues(String database, String tableName,
                                                    String domain, String packageName,
                                                    String outputPath) {
        return buildPlaceholderValues(database, tableName, domain, packageName, outputPath, "5.0");
    }

    /**
     * egovVersion을 지정하여 플레이스홀더 값을 계산합니다.
     */
    public PlaceholderValues buildPlaceholderValues(String database, String tableName,
                                                    String domain, String packageName,
                                                    String outputPath, String egovVersion) {
        // 컬럼 정보 조회
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        if (columns.isEmpty()) return null;

        // PK 탐지
        Map<String, Object> pkCol = columns.stream()
            .filter(c -> "PRI".equals(c.get("COLUMN_KEY")))
            .findFirst()
            .orElse(columns.get(0));

        String pkColumn  = (String) pkCol.get("COLUMN_NAME");
        String pkField   = toCamelCase(pkColumn);
        String pkType    = toJavaType((String) pkCol.get("DATA_TYPE"));

        String domainLc  = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        String domainKr  = extractKoreanName(tableName);
        String urlPrefix = "/" + packageName.replace("egovframework.let.", "").replace(".", "/") + "/" + domainLc;
        String date      = LocalDate.now().toString();

        boolean isJakarta = egovVersion != null
            && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));
        String validationImport = isJakarta
            ? "import jakarta.validation.constraints.NotBlank;\nimport jakarta.validation.constraints.NotNull;\nimport jakarta.validation.constraints.Size;"
            : "import javax.validation.constraints.NotBlank;\nimport javax.validation.constraints.NotNull;\nimport javax.validation.constraints.Size;";

        return new PlaceholderValues(
            packageName,
            domain,
            domainLc,
            domainKr,
            tableName,
            pkField,
            pkColumn,
            pkType,
            urlPrefix,
            date,
            validationImport,
            buildVoFields(columns),
            buildMapperColumns(columns),
            buildInsertColumns(columns),
            buildInsertValues(columns),
            buildUpdateSet(columns, pkColumn),
            buildResultMapFields(columns, packageName, domain),
            buildJspListTh(columns),
            buildJspListTd(columns, pkField),
            buildJspDetailRows(columns),
            buildJspFormInputs(columns, pkColumn)
        );
    }

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath) {
        return buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, "5.0");
    }

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath,
                                      String egovVersion) {
        // 1. 플레이스홀더 값 계산 (공통 메서드 재사용)
        PlaceholderValues pv = buildPlaceholderValues(database, tableName, domain, packageName, outputPath, egovVersion);
        if (pv == null) {
            return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
        }

        // 2. 공통 코드 컬럼 탐지 및 조회
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        String commonCodeSection = buildCommonCodeSection(columns);

        // 3. 통합 프롬프트 조립
        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 5.x CRUD 전체 소스 생성 지시 ===\n\n");

        sb.append("[테이블 정보]\n");
        sb.append("  DB        : ").append(database).append("\n");
        sb.append("  테이블    : ").append(pv.tableName()).append("\n");
        sb.append("  PK 컬럼   : ").append(pv.pkColumn()).append(" (").append(pv.pkType()).append(")\n\n");

        sb.append("[플레이스홀더 치환 규칙]\n");
        sb.append("  {{VALIDATION_IMPORT}} = ").append(pv.validationImport()).append("\n");
        sb.append("  {{PACKAGE}}           = ").append(pv.packageName()).append("\n");
        sb.append("  {{DOMAIN}}            = ").append(pv.domain()).append("\n");
        sb.append("  {{DOMAIN_LC}}         = ").append(pv.domainLc()).append("\n");
        sb.append("  {{DOMAIN_KR}}         = ").append(pv.domainKr()).append("\n");
        sb.append("  {{TABLE_NAME}}        = ").append(pv.tableName()).append("\n");
        sb.append("  {{PK_FIELD}}          = ").append(pv.pkField()).append("\n");
        sb.append("  {{PK_COLUMN}}         = ").append(pv.pkColumn()).append("\n");
        sb.append("  {{PK_TYPE}}           = ").append(pv.pkType()).append("\n");
        sb.append("  {{URL_PREFIX}}        = ").append(pv.urlPrefix()).append("\n");
        sb.append("  {{DATE}}              = ").append(pv.date()).append("\n\n");

        sb.append("  {{VO_FIELDS}}\n").append(pv.voFields()).append("\n");
        sb.append("  {{MAPPER_COLUMNS}}    = ").append(pv.mapperColumns()).append("\n");
        sb.append("  {{INSERT_COLUMNS}}    = ").append(pv.insertColumns()).append("\n");
        sb.append("  {{INSERT_VALUES}}     = ").append(pv.insertValues()).append("\n");
        sb.append("  {{UPDATE_SET}}\n").append(pv.updateSet()).append("\n");
        sb.append("  {{RESULT_MAP_FIELDS}}\n").append(pv.resultMapFields()).append("\n");
        sb.append("  {{JSP_LIST_TH}}\n").append(pv.jspListTh()).append("\n");
        sb.append("  {{JSP_LIST_TD}}\n").append(pv.jspListTd()).append("\n");
        sb.append("  {{JSP_DETAIL_ROWS}}\n").append(pv.jspDetailRows()).append("\n");
        sb.append("  {{JSP_FORM_INPUTS}}\n").append(pv.jspFormInputs()).append("\n");

        if (!commonCodeSection.isEmpty()) {
            sb.append("[공통 코드 참조]\n").append(commonCodeSection).append("\n");
        }

        sb.append(promptBuilder.crudConstraints());

        sb.append("[생성 지시]\n");
        sb.append("generateSource(layer, valuesJson)로 소스를 생성하고 saveGeneratedCode()로 저장하세요.\n");
        sb.append("valuesJson은 위 [플레이스홀더 치환 규칙]의 키-값을 JSON으로 전달하면 됩니다.\n");
        sb.append("출력 경로: ").append(outputPath).append("\n\n");

        String[][] layers = {
            {"vo",               pv.domain() + "VO.java"},
            {"mapper",           pv.domain() + "Mapper.java"},
            {"mapperXml",        pv.domain() + "Mapper.xml"},
            {"service",          pv.domain() + "Service.java"},
            {"serviceImpl",      "Egov" + pv.domain() + "ServiceImpl.java"},
            {"controller",       "Egov" + pv.domain() + "Controller.java"},
            {"controlleradvice", "Egov" + pv.domain() + "ValidationHandler.java"},
            {"jspList",          "Egov" + pv.domain() + "List.jsp"},
            {"jspDetail",        "Egov" + pv.domain() + "Detail.jsp"},
            {"jspRegist",        "Egov" + pv.domain() + "Regist.jsp"},
            {"jspUpdt",          "Egov" + pv.domain() + "Updt.jsp"},
        };
        for (int i = 0; i < layers.length; i++) {
            sb.append(String.format("  Step %2d: getCodeTemplate(\"%s\") → %s\n",
                i + 1, layers[i][0], layers[i][1]));
        }

        sb.append("\n").append(promptBuilder.postGeneration(outputPath, tableName, domain, packageName, pv.domainLc(), "11개 파일"));

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
            String field    = toCamelCase((String) col.get("COLUMN_NAME"));
            String javaType = toJavaType((String) col.get("DATA_TYPE"));
            String nullable = (String) col.get("IS_NULLABLE");
            Object maxLen   = col.get("CHARACTER_MAXIMUM_LENGTH");
            String comment  = col.get("COLUMN_COMMENT") != null ? (String) col.get("COLUMN_COMMENT") : "";
            boolean isPk    = "PRI".equals(col.get("COLUMN_KEY"));

            if (!comment.isEmpty()) sb.append("    // ").append(comment).append("\n");

            if (!isPk && "NO".equals(nullable)) {
                sb.append("    @").append("String".equals(javaType) ? "NotBlank" : "NotNull").append("\n");
            }
            if (!isPk && maxLen != null && "String".equals(javaType)) {
                sb.append("    @Size(max = ").append(maxLen).append(")\n");
            }
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
            sb.append("                                <td><c:out value=\"${item.").append(field).append("}\"/></td>\n");
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
