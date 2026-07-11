package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterDetailService {

    private final JdbcTemplate jdbcTemplate;
    private final TableRelationService tableRelationService;
    private final EgovPromptBuilder promptBuilder;

    // ── buildMasterDetailPrompt ───────────────────────────────────────────────
    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath) {
        return buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName, outputPath, "jsp");
    }

    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath,
                                          String viewType) {
        return buildMasterDetailPrompt(database, masterTable, detailTable, domain, packageName, outputPath,
                viewType, null, null, null);
    }

    public String buildMasterDetailPrompt(String database, String masterTable, String detailTable,
                                          String domain, String packageName, String outputPath,
                                          String viewType,
                                          String layoutMode,
                                          String layoutView,
                                          String breadcrumbView) {
        List<Map<String, Object>> masterCols = fetchColumns(database, masterTable);
        List<Map<String, Object>> detailCols = fetchColumns(database, detailTable);

        if (masterCols.isEmpty()) return "마스터 테이블을 찾을 수 없습니다: " + masterTable;
        if (detailCols.isEmpty()) return "디테일 테이블을 찾을 수 없습니다: " + detailTable;

        // PK 탐지
        Map<String, Object> masterPkCol = masterCols.stream()
            .filter(c -> "PRI".equals(c.get("COLUMN_KEY"))).findFirst()
            .orElse(masterCols.get(0));
        Map<String, Object> detailPkCol = detailCols.stream()
            .filter(c -> "PRI".equals(c.get("COLUMN_KEY"))).findFirst()
            .orElse(detailCols.get(0));

        String masterPkColumn = (String) masterPkCol.get("COLUMN_NAME");
        String masterPkField  = toCamelCase(masterPkColumn);
        String detailPkColumn = (String) detailPkCol.get("COLUMN_NAME");
        String detailPkField  = toCamelCase(detailPkColumn);

        // FK 컬럼 탐지 (디테일 테이블에서 마스터 PK와 동일한 컬럼명)
        String fkColumn = detailCols.stream()
            .map(c -> (String) c.get("COLUMN_NAME"))
            .filter(col -> col.equals(masterPkColumn))
            .findFirst()
            .orElse(masterPkColumn);

        String detailDomain   = deriveDetailDomain(detailTable);
        String detailDomainLc = detailDomain.substring(0, 1).toLowerCase() + detailDomain.substring(1);
        String domainLc       = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        String urlPrefix      = "/" + packageName.replace("egovframework.let.", "").replace(".", "/") + "/" + domainLc;
        String date           = LocalDate.now().toString();
        String resolvedViewType = normalizeViewType(viewType);
        boolean thymeleaf = "thymeleaf".equals(resolvedViewType);
        CrudLayoutMode resolvedLayoutMode = thymeleaf ? CrudLayoutMode.from(layoutMode) : CrudLayoutMode.CREATE;
        if (thymeleaf && resolvedLayoutMode == CrudLayoutMode.NONE) {
            throw new IllegalArgumentException("layoutMode=none은 아직 지원하지 않습니다.");
        }
        String resolvedLayoutView = layoutView == null || layoutView.isBlank() ? "layout/default" : layoutView;
        String resolvedBreadcrumbView = breadcrumbView == null || breadcrumbView.isBlank() ? "layout/breadcrumb" : breadcrumbView;
        String fileCount = thymeleaf && resolvedLayoutMode == CrudLayoutMode.CREATE ? "18개 파일"
                : thymeleaf ? "13개 파일" : "13개 파일";

        StringBuilder sb = new StringBuilder();
        sb.append("=== eGovFrame 5.x 마스터-디테일 CRUD 소스 생성 지시 ===\n\n");

        sb.append("[구조]\n");
        sb.append("  마스터: ").append(masterTable).append(" → ").append(domain).append("VO.java\n");
        sb.append("  디테일: ").append(detailTable).append(" → ").append(detailDomain).append("VO.java\n");
        sb.append("  FK 컬럼: ").append(fkColumn).append(" (마스터 PK → 디테일에서 참조)\n\n");

        sb.append("[플레이스홀더 — 마스터]\n");
        sb.append("  {{PACKAGE}}       = ").append(packageName).append("\n");
        sb.append("  {{DOMAIN}}        = ").append(domain).append("\n");
        sb.append("  {{DOMAIN_LC}}     = ").append(domainLc).append("\n");
        sb.append("  {{TABLE_NAME}}    = ").append(masterTable).append("\n");
        sb.append("  {{PK_FIELD}}      = ").append(masterPkField).append("\n");
        sb.append("  {{PK_COLUMN}}     = ").append(masterPkColumn).append("\n");
        sb.append("  {{URL_PREFIX}}    = ").append(urlPrefix).append("\n");
        sb.append("  {{DATE}}          = ").append(date).append("\n\n");
        sb.append("[화면 타입]\n");
        sb.append("  viewType          = ").append(resolvedViewType).append("\n");
        sb.append("  정적 리소스       = /resources/css/styles.css, /resources/js/krds.min.js\n");
        sb.append("  자산 URL 정책     = WAR/BOOT 모두 /resources/** 유지 (BOOT 파일은 static/resources/** 에 생성)\n");
        sb.append("  전제 조건         = initializeProject()가 styles.css, _ds_bundle.css, krds.min.js를 생성해야 합니다.\n");
        sb.append("  모델 속성 계약     = lnbTitle, lnbMenus, breadcrumbs, currentMenuId\n");
        if (thymeleaf) {
            sb.append("  화면 경로         = src/main/resources/templates/").append(domainLc).append("/Egov").append(domain).append("*.html\n");
            sb.append("  레이아웃 참조     = ").append(resolvedLayoutView).append("\n");
            sb.append("  breadcrumb 참조   = ").append(resolvedBreadcrumbView).append("\n");
            if (resolvedLayoutMode == CrudLayoutMode.CREATE) {
                sb.append("  layout 생성       = layout/default.html, gnb.html, lnb.html, breadcrumb.html, footer.html 포함\n\n");
            } else {
                sb.append("  layout 생성       = 하지 않음. generateThymeleafLayout()로 먼저 준비된 layout 재사용\n\n");
            }
        } else {
            sb.append("  화면 경로         = src/main/webapp/WEB-INF/jsp/").append(domainLc).append("/Egov").append(domain).append("*.jsp\n\n");
        }

        sb.append("[플레이스홀더 — 디테일]\n");
        sb.append("  DETAIL_DOMAIN     = ").append(detailDomain).append("\n");
        sb.append("  DETAIL_DOMAIN_LC  = ").append(detailDomainLc).append("\n");
        sb.append("  DETAIL_TABLE      = ").append(detailTable).append("\n");
        sb.append("  DETAIL_PK_FIELD   = ").append(detailPkField).append("\n");
        sb.append("  DETAIL_PK_COLUMN  = ").append(detailPkColumn).append("\n");
        sb.append("  FK_COLUMN         = ").append(fkColumn).append("\n\n");

        sb.append("[마스터 VO 필드]\n").append(buildVoFields(masterCols)).append("\n");
        sb.append("[디테일 VO 필드]\n").append(buildVoFields(detailCols)).append("\n");

        sb.append("[생성 파일 목록 — ").append(fileCount).append("]\n");
        sb.append("  Step  1: ").append(domain).append("VO.java                    ← 마스터 VO\n");
        sb.append("  Step  2: ").append(detailDomain).append("VO.java                ← 디테일 VO\n");
        sb.append("  Step  3: ").append(domain).append("Mapper.java                ← 마스터 Mapper 인터페이스\n");
        sb.append("  Step  4: ").append(detailDomain).append("Mapper.java            ← 디테일 Mapper 인터페이스\n");
        sb.append("  Step  5: ").append(domain).append("Mapper.xml                 ← 마스터 SQL + 디테일 목록 쿼리\n");
        sb.append("  Step  6: ").append(detailDomain).append("Mapper.xml             ← 디테일 CRUD SQL\n");
        sb.append("  Step  7: ").append(domain).append("Service.java               ← 마스터 Service 인터페이스\n");
        sb.append("  Step  8: Egov").append(domain).append("ServiceImpl.java        ← 마스터+디테일 목록 조회\n");
        sb.append("  Step  9: Egov").append(domain).append("Controller.java         ← 상세 진입 시 디테일 로드\n");
        sb.append("  Step 10: Egov").append(domain).append("ValidationHandler.java  ← Validation 전역 예외 핸들러\n");
        if (thymeleaf) {
            if (resolvedLayoutMode == CrudLayoutMode.CREATE) {
                sb.append("  Step 11: layout/default.html              ← Thymeleaf 공통 레이아웃\n");
                sb.append("  Step 12: layout/gnb.html                  ← 상단 GNB partial\n");
                sb.append("  Step 13: layout/lnb.html                  ← 좌측 LNB partial\n");
                sb.append("  Step 14: layout/breadcrumb.html           ← breadcrumb partial\n");
                sb.append("  Step 15: layout/footer.html               ← footer partial\n");
                sb.append("  Step 16: Egov").append(domain).append("List.html               ← 마스터 목록\n");
                sb.append("  Step 17: Egov").append(domain).append("Detail.html             ← 마스터 상세 + 디테일 그리드 탭\n");
                sb.append("  Step 18: Egov").append(domain).append("Regist.html             ← 마스터 등록\n\n");
            } else {
                sb.append("  Step 11: Egov").append(domain).append("List.html               ← 마스터 목록\n");
                sb.append("  Step 12: Egov").append(domain).append("Detail.html             ← 마스터 상세 + 디테일 그리드 탭\n");
                sb.append("  Step 13: Egov").append(domain).append("Regist.html             ← 마스터 등록\n\n");
            }
        } else {
            sb.append("  Step 11: Egov").append(domain).append("List.jsp                ← 마스터 목록\n");
            sb.append("  Step 12: Egov").append(domain).append("Detail.jsp              ← 마스터 상세 + 디테일 그리드 탭\n");
            sb.append("  Step 13: Egov").append(domain).append("Regist.jsp              ← 마스터 등록\n\n");
        }

        sb.append("[Step 5 — 마스터 Mapper XML 핵심 패턴]\n");
        sb.append("  <!-- 디테일 목록 조회 (마스터 PK로 조회) -->\n");
        sb.append("  <select id=\"select").append(detailDomain).append("List\" ")
          .append("parameterType=\"String\" resultType=\"").append(packageName)
          .append(".service.").append(detailDomain).append("VO\">\n");
        sb.append("    SELECT * FROM ").append(detailTable).append("\n");
        sb.append("    WHERE ").append(fkColumn).append(" = #{").append(masterPkField).append("}\n");
        sb.append("  </select>\n\n");

        sb.append("[Step 8 — ServiceImpl 추가 메서드]\n");
        sb.append("  List<").append(detailDomain).append("VO> select").append(detailDomain)
          .append("List(String ").append(masterPkField).append(") throws Exception;\n\n");

        sb.append("[Step 9 — Controller 상세 조회 패턴]\n");
        sb.append("  @RequestMapping(\"").append(urlPrefix).append("Detail.do\")\n");
        sb.append("  public String select").append(domain).append("(\n");
        sb.append("          @ModelAttribute(\"searchVO\") ").append(domain).append("VO searchVO,\n");
        sb.append("          ModelMap model) throws Exception {\n");
        sb.append("      ").append(domain).append("VO vo = ").append(domainLc)
          .append("Service.select").append(domain).append("(searchVO);\n");
        sb.append("      List<").append(detailDomain).append("VO> detailList = ").append(domainLc)
          .append("Service.select").append(detailDomain).append("List(searchVO.get")
          .append(masterPkField.substring(0, 1).toUpperCase()).append(masterPkField.substring(1))
          .append("());\n");
        sb.append("      model.addAttribute(\"result\", vo);\n");
        sb.append("      model.addAttribute(\"detailList\", detailList);\n");
        sb.append("      return \"").append(domainLc).append("/Egov").append(domain).append("Detail\";\n");
        sb.append("  }\n\n");

        if (thymeleaf) {
            appendThymeleafDetailPattern(sb, detailCols, detailDomain, domain, resolvedLayoutMode, resolvedLayoutView);
        } else {
            appendJspDetailPattern(sb, detailCols, detailDomain);
        }

        sb.append(promptBuilder.postGeneration(outputPath, masterTable + "+" + detailTable, domain, packageName, domainLc, fileCount));

        log.info("마스터-디테일 프롬프트 빌드 완료: master={}, detail={}", masterTable, detailTable);
        return sb.toString();
    }

    // ── buildJoinSelectPrompt ─────────────────────────────────────────────────
    public String buildJoinSelectPrompt(String database, String tableName) {
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        if (columns.isEmpty()) return "테이블을 찾을 수 없습니다: " + tableName;

        List<TableRelationService.RelationInfo> implicit =
            tableRelationService.getImplicitJoinCandidates(database, tableName);
        List<TableRelationService.RelationInfo> codeJoins =
            tableRelationService.getCommonCodeJoinCandidates(database, tableName);

        if (implicit.isEmpty() && codeJoins.isEmpty()) {
            return "JOIN 후보 컬럼이 없습니다. 단순 단일 테이블 구조이므로 buildFullCrudPrompt()를 사용하세요.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(tableName).append(" JOIN SELECT 소스 생성 지시 ===\n\n");

        // 1. JOIN 후보 요약
        sb.append("[탐지된 JOIN 후보]\n");
        sb.append(String.format("  %-30s %-35s %s\n", "컬럼", "JOIN 대상 테이블", "JOIN 조건"));
        sb.append("  " + "-".repeat(85) + "\n");

        List<JoinEntry> joins = new ArrayList<>();
        int aliasIdx = 1;

        for (TableRelationService.RelationInfo r : implicit) {
            String alias = "t" + aliasIdx++;
            joins.add(new JoinEntry(r.sourceColumn(), r.targetTable(), r.targetColumn(), alias, false));
            sb.append(String.format("  %-30s %-35s %s.%s = %s\n",
                r.sourceColumn(), r.targetTable() + " (" + alias + ")",
                alias, r.targetColumn(), r.sourceColumn()));
        }
        for (TableRelationService.RelationInfo r : codeJoins) {
            String alias = "cd" + aliasIdx++;
            joins.add(new JoinEntry(r.sourceColumn(), "COMTCCMMNDETAILCODE", "CODE", alias, true));
            sb.append(String.format("  %-30s %-35s %s\n",
                r.sourceColumn(), "COMTCCMMNDETAILCODE (" + alias + ")",
                "CODE_ID='???' AND " + alias + ".CODE = " + r.sourceColumn()));
        }
        sb.append("\n");

        // 2. VO 추가 필드
        sb.append("\n[VO 추가 필드 — 기존 VO에 추가하세요]\n");
        for (JoinEntry j : joins) {
            j.fieldName = toCamelCase(j.sourceCol) + "Nm";
            j.colAlias  = j.sourceCol + "_NM";
            sb.append("  private String ").append(j.fieldName).append(";")
              .append("  // ").append(j.targetTable).append(" 조인용\n");
        }

        // 3. SELECT 쿼리
        sb.append("\n[Mapper XML — selectList 수정 패턴]\n");
        sb.append("  <select id=\"select").append(deriveDetailDomain(tableName))
          .append("List\" ...>\n");
        sb.append("    SELECT\n");
        sb.append("        e.*");
        for (JoinEntry j : joins) {
            if (j.isCommonCode) {
                sb.append(",\n        ").append(j.alias).append(".CODE_NM AS ")
                  .append(j.colAlias);
            } else {
                sb.append(",\n        ").append(j.alias).append(".NAME_COLUMN AS ")
                  .append(j.colAlias)
                  .append("  /* ").append(j.targetTable).append("의 명칭 컬럼명으로 교체 */");
            }
        }
        sb.append("\n    FROM ").append(tableName).append(" e\n");
        for (JoinEntry j : joins) {
            if (j.isCommonCode) {
                sb.append("    LEFT JOIN COMTCCMMNDETAILCODE ").append(j.alias).append("\n");
                sb.append("           ON ").append(j.alias)
                  .append(".CODE_ID = '???'  /* 실제 CODE_ID 값으로 교체 */\n");
                sb.append("          AND ").append(j.alias).append(".CODE = e.")
                  .append(j.sourceCol).append("\n");
            } else {
                sb.append("    LEFT JOIN ").append(j.targetTable).append(" ").append(j.alias).append("\n");
                sb.append("           ON ").append(j.alias).append(".").append(j.targetCol)
                  .append(" = e.").append(j.sourceCol).append("\n");
            }
        }
        sb.append("    <where>...</where>\n");
        sb.append("  </select>\n\n");

        // 4. resultMap 추가
        sb.append("[Mapper XML — resultMap 추가 항목]\n");
        for (JoinEntry j : joins) {
            sb.append("  <result property=\"").append(j.fieldName)
              .append("\" column=\"").append(j.colAlias).append("\"/>\n");
        }

        sb.append("\n[주의사항]\n");
        sb.append("  - 공통코드 JOIN의 CODE_ID는 COMTCCMMNCODE 테이블에서 실제 값을 확인하세요.\n");
        sb.append("  - 각 JOIN 테이블의 명칭 컬럼명(NAME_COLUMN)은 실제 컬럼명으로 교체하세요.\n");
        sb.append("  - generateSource() 대신 직접 Mapper XML을 수정하는 방식으로 적용하세요.\n");

        log.info("JOIN SELECT 프롬프트 빌드 완료: table={}, joinCount={}", tableName, joins.size());
        return sb.toString();
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────
    private static class JoinEntry {
        final String sourceCol;
        final String targetTable;
        final String targetCol;
        final String alias;
        final boolean isCommonCode;
        String fieldName;
        String colAlias;

        JoinEntry(String sourceCol, String targetTable, String targetCol,
                  String alias, boolean isCommonCode) {
            this.sourceCol    = sourceCol;
            this.targetTable  = targetTable;
            this.targetCol    = targetCol;
            this.alias        = alias;
            this.isCommonCode = isCommonCode;
        }
    }

    private List<Map<String, Object>> fetchColumns(String database, String tableName) {
        return jdbcTemplate.queryForList(
            "SELECT c.COLUMN_NAME, c.DATA_TYPE, c.CHARACTER_MAXIMUM_LENGTH, c.IS_NULLABLE, c.COLUMN_COMMENT, " +
            "  CASE WHEN kcu.COLUMN_NAME IS NOT NULL THEN 'PRI' ELSE '' END AS COLUMN_KEY " +
            "FROM INFORMATION_SCHEMA.COLUMNS c " +
            "LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
            "  ON kcu.TABLE_SCHEMA = c.TABLE_SCHEMA AND kcu.TABLE_NAME = c.TABLE_NAME " +
            "  AND kcu.COLUMN_NAME = c.COLUMN_NAME AND kcu.CONSTRAINT_NAME = 'PRIMARY' " +
            "WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ? ORDER BY c.ORDINAL_POSITION",
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

            if (!comment.isBlank()) sb.append("  // ").append(comment).append("\n");

            if (!isPk && "NO".equals(nullable)) {
                sb.append("  @").append("String".equals(javaType) ? "NotBlank" : "NotNull").append("\n");
            }
            if (!isPk && maxLen != null && "String".equals(javaType)) {
                sb.append("  @Size(max = ").append(maxLen).append(")\n");
            }
            sb.append("  private ").append(javaType).append(" ").append(field).append(";\n");
        }
        return sb.toString();
    }

    private List<String> buildVoFieldNames(List<Map<String, Object>> columns) {
        return columns.stream()
            .map(c -> (String) c.get("COLUMN_NAME"))
            .toList();
    }

    private void appendJspDetailPattern(StringBuilder sb, List<Map<String, Object>> detailCols,
                                        String detailDomain) {
        sb.append("[Step 12 — Detail JSP 디테일 그리드 탭 패턴]\n");
        sb.append("  <!-- 마스터 정보 섹션 -->\n");
        sb.append("  <div id=\"masterSection\">\n");
        sb.append("    <table>...마스터 필드...</table>\n");
        sb.append("  </div>\n\n");
        sb.append("  <!-- 디테일 그리드 탭 -->\n");
        sb.append("  <div id=\"detailSection\">\n");
        sb.append("    <h3>").append(detailDomain).append(" 목록</h3>\n");
        sb.append("    <table>\n");
        sb.append("      <thead><tr>\n");
        buildVoFieldNames(detailCols).forEach(f ->
            sb.append("        <th>").append(f).append("</th>\n"));
        sb.append("      </tr></thead>\n");
        sb.append("      <tbody>\n");
        sb.append("      <c:forEach items=\"${detailList}\" var=\"detail\">\n");
        sb.append("        <tr>\n");
        buildVoFieldNames(detailCols).forEach(f ->
            sb.append("          <td><c:out value=\"${detail.").append(toCamelCase(f)).append("}\"/></td>\n"));
        sb.append("        </tr>\n");
        sb.append("      </c:forEach>\n");
        sb.append("      </tbody>\n");
        sb.append("    </table>\n");
        sb.append("  </div>\n\n");
    }

    private void appendThymeleafDetailPattern(StringBuilder sb, List<Map<String, Object>> detailCols,
                                              String detailDomain, String domain,
                                              CrudLayoutMode layoutMode,
                                              String layoutView) {
        if (layoutMode == CrudLayoutMode.CREATE) {
            sb.append("[Thymeleaf layout/default.html 핵심 패턴]\n");
            sb.append("  <!DOCTYPE html>\n");
            sb.append("  <html xmlns:th=\"http://www.thymeleaf.org\" xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\">\n");
            sb.append("  <head>\n");
            sb.append("    <link rel=\"stylesheet\" th:href=\"@{/resources/css/styles.css}\">\n");
            sb.append("  </head>\n");
            sb.append("  <body>\n");
            sb.append("    <main layout:fragment=\"content\"></main>\n");
            sb.append("    <script th:src=\"@{/resources/js/krds.min.js}\"></script>\n");
            sb.append("  </body>\n");
            sb.append("  </html>\n\n");
        } else {
            sb.append("[Thymeleaf layout 재사용]\n");
            sb.append("  layout 파일은 생성하지 않습니다. 먼저 generateThymeleafLayout(outputPath=..., layoutBasePath=\"layout\")를 실행하세요.\n\n");
        }

        sb.append("[Detail Thymeleaf 디테일 그리드 탭 패턴]\n");
        sb.append("  <html xmlns:th=\"http://www.thymeleaf.org\"\n");
        sb.append("        xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\"\n");
        sb.append("        layout:decorate=\"~{").append(layoutView).append("}\">\n");
        sb.append("  <main layout:fragment=\"content\">\n");
        sb.append("    <!-- 마스터 정보 섹션 -->\n");
        sb.append("    <section id=\"masterSection\">\n");
        sb.append("      <table>...마스터 필드...</table>\n");
        sb.append("    </section>\n\n");
        sb.append("    <!-- 디테일 그리드 탭 -->\n");
        sb.append("    <section id=\"detailSection\">\n");
        sb.append("      <h3>").append(detailDomain).append(" 목록</h3>\n");
        sb.append("      <table>\n");
        sb.append("        <thead><tr>\n");
        buildVoFieldNames(detailCols).forEach(f ->
            sb.append("          <th>").append(f).append("</th>\n"));
        sb.append("        </tr></thead>\n");
        sb.append("        <tbody>\n");
        sb.append("          <tr th:each=\"detail : ${detailList}\">\n");
        buildVoFieldNames(detailCols).forEach(f ->
            sb.append("            <td th:text=\"${detail.").append(toCamelCase(f)).append("}\"></td>\n"));
        sb.append("          </tr>\n");
        sb.append("          <tr th:if=\"${#lists.isEmpty(detailList)}\">\n");
        sb.append("            <td colspan=\"").append(detailCols.size()).append("\">등록된 ").append(detailDomain).append(" 정보가 없습니다.</td>\n");
        sb.append("          </tr>\n");
        sb.append("        </tbody>\n");
        sb.append("      </table>\n");
        sb.append("    </section>\n");
        sb.append("  </main>\n");
        sb.append("  </html>\n\n");
        sb.append("[Thymeleaf 생성 제약]\n");
        sb.append("  - JSP taglib, <c:forEach>, <c:out>, form 태그는 사용하지 마세요.\n");
        sb.append("  - layout/default.html은 /resources/css/styles.css와 /resources/js/krds.min.js만 직접 링크하세요.\n");
        sb.append("  - _ds_bundle.css는 styles.css 내부 @import로 포함되므로 화면 템플릿에서 별도 링크하지 마세요.\n");
        sb.append("  - Controller는 lnbTitle, lnbMenus, breadcrumbs, currentMenuId를 모든 Thymeleaf 화면 진입 전에 설정하세요.\n");
        sb.append("  - 화면 파일은 src/main/resources/templates/").append(domain.substring(0, 1).toLowerCase()).append(domain.substring(1))
          .append("/Egov").append(domain).append("*.html 경로로 생성하세요.\n");
        sb.append("  - Controller return 값은 기존과 동일하게 \"").append(domain.substring(0, 1).toLowerCase()).append(domain.substring(1))
          .append("/Egov").append(domain).append("Detail\" 형식을 유지하세요.\n\n");
    }

    private String normalizeViewType(String viewType) {
        if (viewType == null || viewType.isBlank()) {
            return "jsp";
        }
        return switch (viewType.trim().toLowerCase()) {
            case "thymeleaf", "th", "html" -> "thymeleaf";
            case "jsp" -> "jsp";
            default -> throw new IllegalArgumentException("지원하지 않는 viewType: " + viewType + " (지원값: jsp, thymeleaf)");
        };
    }

    private String deriveDetailDomain(String tableName) {
        String base = tableName.replace("COMTN", "").replace("COMTS", "").replace("COMTC", "");
        String[] parts = base.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private String toCamelCase(String columnName) {
        if (columnName == null) return "";
        String[] parts = columnName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty())
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private String toJavaType(String dataType) {
        if (dataType == null) return "String";
        return switch (dataType.toLowerCase()) {
            case "int", "tinyint", "smallint", "mediumint" -> "Integer";
            case "bigint"                                   -> "Long";
            case "decimal", "numeric", "float", "double"   -> "java.math.BigDecimal";
            default                                         -> "String";
        };
    }
}
