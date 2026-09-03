package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.policy.SensitiveFieldPolicy;
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

    private static final String KRDS_ASSET_WARNING = """
            ⚠️ 이 프로젝트에 _ds_bundle.css/krds.min.js가 없습니다.
               코드 생성 전에 ProjectInitializrTool.initializeProject()를 먼저 실행하세요.

            """;

    private final JdbcTemplate jdbcTemplate;
    private final CommonCodeService commonCodeService;
    private final EgovPromptBuilder promptBuilder;
    private final CrudSchemaQueryService crudSchemaQueryService;
    private final ScreenSpecificationPromptFormatter screenSpecificationPromptFormatter;

    /**
     * Claude 프롬프트의 레거시 플레이스홀더 계약과 스키마 설명에 필요한 값을 담는 레코드.
     * auto 모드는 이 Map 치환 경로가 아니라 CrudModelFactory와 FreeMarker renderer를 사용합니다.
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
        /** 기존 Java 호출자의 플레이스홀더 계약 호환을 위한 Map 변환. */
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
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        return buildPlaceholderValuesFromColumns(columns, database, tableName, domain, packageName, outputPath, egovVersion);
    }

    private PlaceholderValues buildPlaceholderValuesFromColumns(
            List<Map<String, Object>> columns, String database, String tableName,
            String domain, String packageName, String outputPath, String egovVersion) {
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
        List<Map<String, Object>> safeDetailColumns = columns.stream()
                .filter(column -> !isSensitiveDisplayColumn(column))
                .toList();

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
            buildJspDetailRows(safeDetailColumns),
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
        return buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, egovVersion, "jsp");
    }

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath,
                                      String egovVersion, String viewType) {
        return buildFullCrudPrompt(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, null, null, null);
    }

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath,
                                      String egovVersion, String viewType,
                                      String layoutMode, String layoutView, String breadcrumbView) {
        return buildFullCrudPrompt(database, tableName, domain, packageName, outputPath, egovVersion, viewType,
                layoutMode, layoutView, breadcrumbView, CrudProgramMetadata.fallback(null));
    }

    /**
     * llmProvider="claude" 경로에서도 auto 모드(CrudOrchestrationService)와 동일한
     * LETTNPROGRMLIST 메타데이터를 반영한다 — 화면 한글명과 GET 등록 URL(list/detail/registView/updtView)을
     * 프롬프트에 포함해 Claude가 수작업으로 만드는 Controller도 동일한 alias를 갖도록 안내한다.
     */
    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath,
                                      String egovVersion, String viewType,
                                      String layoutMode, String layoutView, String breadcrumbView,
                                      CrudProgramMetadata metadata) {
        return buildFullCrudPrompt(database, tableName, domain, packageName, outputPath,
                egovVersion, viewType, layoutMode, layoutView, breadcrumbView, metadata, null);
    }

    public String buildFullCrudPrompt(String database, String tableName,
                                      String domain, String packageName, String outputPath,
                                      String egovVersion, String viewType,
                                      String layoutMode, String layoutView, String breadcrumbView,
                                      CrudProgramMetadata metadata,
                                      ScreenSpecification screenSpecification) {
        CrudProgramMetadata md =
                metadata == null ? CrudProgramMetadata.fallback(null) : metadata;
        if (md.blocksGeneration()) {
            return "프로그램 메타데이터 검증 실패: " + md.message();
        }
        CrudViewType resolvedViewType = CrudViewType.from(viewType);
        CrudLayoutMode resolvedLayoutMode = resolvedViewType == CrudViewType.THYMELEAF
                ? CrudLayoutMode.from(layoutMode)
                : CrudLayoutMode.CREATE;
        String resolvedLayoutView = layoutView == null || layoutView.isBlank() ? "layout/default" : layoutView;
        String resolvedBreadcrumbView = breadcrumbView == null || breadcrumbView.isBlank() ? "layout/breadcrumb" : breadcrumbView;
        // 1. 컬럼 정보 1회 조회 — 플레이스홀더 계산과 공통 코드 탐지에 재사용
        List<Map<String, Object>> columns = fetchColumns(database, tableName);
        PlaceholderValues pv = buildPlaceholderValuesFromColumns(columns, database, tableName, domain, packageName, outputPath, egovVersion);
        if (pv == null) {
            return "테이블을 찾을 수 없습니다: " + database + "." + tableName;
        }

        // 2. 공통 코드 컬럼 탐지 (조회된 컬럼 재사용)
        String commonCodeSection = buildCommonCodeSection(columns);

        // 3. 통합 프롬프트 조립
        StringBuilder sb = new StringBuilder();
        if (!KrdsAssetVerifier.hasCompleteAssets(outputPath)) {
            sb.append(KRDS_ASSET_WARNING);
        }
        sb.append("=== eGovFrame 5.x CRUD 전체 소스 생성 지시 ===\n\n");
        if (screenSpecification != null) {
            sb.append(screenSpecificationPromptFormatter.format(screenSpecification)).append('\n');
            if (!screenSpecification.componentGeometry().isEmpty()) {
                sb.append("[디자인 기하 정보 사용 규칙]\n")
                        .append("- componentGeometry는 Figma 원본 좌표·간격·색상·폰트 참고값입니다.\n")
                        .append("- 화면 구조와 마크업은 반드시 기존 krds-*/egov-* 클래스 체계를 그대로 유지하세요.\n")
                        .append("- 임의의 커스텀 클래스나 인라인 style로 KRDS 구조를 대체하지 마세요.\n")
                        .append("- 좌표를 인라인 style이나 고정 px width/height로 옮기면 반응형이 깨집니다. ")
                        .append("표·검색패널 등 기존 KRDS 클래스에는 이미 반응형 @media 규칙이 내장되어 ")
                        .append("있으니(예: krds-table-wrap의 767px 이하 모바일 대응) 클래스만 정확히 ")
                        .append("유지하세요.\n\n");
            }
            boolean detailSubsetRequested = screenSpecification.pages().stream()
                    .filter(page -> "detail".equalsIgnoreCase(page.id()))
                    .findFirst()
                    .map(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                    .orElse(false);
            if (resolvedViewType == CrudViewType.JSP && detailSubsetRequested) {
                sb.append("[호환성 경고]\n")
                        .append("- JSP 생성에서는 detail 화면 필드 subset을 지원하지 않아 표준 상세 필드를 사용합니다.\n\n");
            }
        }
        List<String> excludedDetailColumns = columns.stream()
                .filter(this::isSensitiveDisplayColumn)
                .map(column -> String.valueOf(column.get("COLUMN_NAME")))
                .toList();
        if (!excludedDetailColumns.isEmpty()) {
            sb.append("[상세 화면 민감정보 제외 계약]\n");
            sb.append("- 상세 화면에는 다음 필드를 표시하지 마세요: ")
                    .append(String.join(", ", excludedDetailColumns)).append("\n");
            sb.append("- JSP와 Thymeleaf 모두 동일하며, VO/Mapper/등록·수정 폼의 스키마 필드는 유지하세요.\n\n");
        }

        sb.append("[테이블 정보]\n");
        sb.append("  DB        : ").append(database).append("\n");
        sb.append("  테이블    : ").append(pv.tableName()).append("\n");
        sb.append("  PK 컬럼   : ").append(pv.pkColumn()).append(" (").append(pv.pkType()).append(")\n\n");

        sb.append("[플레이스홀더 치환 규칙]\n");
        sb.append("  {{VALIDATION_IMPORT}} = ").append(pv.validationImport()).append("\n");
        sb.append("  {{PACKAGE}}           = ").append(pv.packageName()).append("\n");
        sb.append("  {{DOMAIN}}            = ").append(pv.domain()).append("\n");
        sb.append("  {{DOMAIN_LC}}         = ").append(pv.domainLc()).append("\n");
        String resolvedDomainKr = md.programKoreanName() != null
                ? com.krdevops.springai.util.CrudMappingUtils.stripScreenTypeSuffix(md.programKoreanName())
                : pv.domainKr();
        sb.append("  {{DOMAIN_KR}}         = ").append(resolvedDomainKr).append("\n");
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

        if (!md.registeredPathByRole().isEmpty()) {
            sb.append("[LETTNPROGRMLIST 등록 URL — Controller에 추가 alias로 포함하세요]\n");
            sb.append("  GNB/LNB 메뉴가 클릭하는 실제 URL이므로, canonical URL({{URL_PREFIX}}+화면)을 ");
            sb.append("주 매핑으로 유지한 채 아래 URL을 같은 GET 핸들러의 @GetMapping({...}) 배열에 추가하세요:\n");
            appendRegisteredRole(sb, md, CrudProgramMetadataService.ROLE_LIST, "목록(List)");
            appendRegisteredRole(sb, md, CrudProgramMetadataService.ROLE_DETAIL, "상세(Detail)");
            appendRegisteredRole(sb, md, CrudProgramMetadataService.ROLE_REGIST_VIEW, "등록화면(RegistView)");
            appendRegisteredRole(sb, md, CrudProgramMetadataService.ROLE_UPDT_VIEW, "수정화면(UpdtView)");
            sb.append("  (등록/수정/삭제 처리 URL은 POST 전용이라 DB 등록 URL을 그대로 alias로 붙이지 않습니다.)\n\n");
        }

        String menuContextUrlValue = md.registeredListUrl() != null
                ? md.registeredListUrl() : pv.urlPrefix() + "List.do";
        sb.append("[메뉴 문맥 유지 — auto 모드(CrudOrchestrationService)와 동일하게 구현하세요]\n");
        sb.append("  목록 화면만 LETTNMENUINFO에 등록되고 상세/등록/수정 화면은 등록되지 않는 경우가 ");
        sb.append("일반적입니다. EgovGnbMenuInterceptor가 LNB/브레드크럼을 채울 때 이 화면들도 목록의 ");
        sb.append("메뉴 문맥을 쓰도록, 뷰를 반환하는 모든 핸들러에서 아래 두 model 속성을 반드시 채우세요:\n");
        sb.append("  - menuContextUrl : 이 화면이 속한 목록 화면의 최종 URL. 우선순위는 다음과 같습니다 — ");
        sb.append("(1) LETTNPROGRMLIST에 목록 프로그램이 등록돼 있으면 그 원본 URL을 쿼리스트링까지 ");
        sb.append("그대로(예: \"...?bbsId=BBS_NOTICE\") 사용하세요 — path만 쓰면 같은 path에 bbsId만 다른 ");
        sb.append("여러 게시판 메뉴(흔한 패턴)를 구분하지 못합니다. (2) 등록이 없으면 canonical list path만 ");
        sb.append("(쿼리스트링 없이) 사용하세요. 이 화면의 값: \"").append(menuContextUrlValue).append("\"\n");
        sb.append("  - currentPageSuffix : 화면 종류 — 목록/상세/등록/수정 (인터셉터가 프로그램 한글명 ");
        sb.append("뒤에 붙여 브레드크럼 마지막 항목을 만듭니다, 예: \"공지사항 상세\")\n\n");

        sb.append(promptBuilder.crudConstraints());
        appendViewTypeInstruction(sb, resolvedViewType, resolvedLayoutMode, resolvedLayoutView, resolvedBreadcrumbView);

        // CrudLayerDefinition.LAYERS 공유 — auto 모드(CrudOrchestrationService)와 경로 정의 일원화
        // ⚠️ CrudLayerDefinition 템플릿은 egovframework/let/{PKG}/... 고정이므로
        //    packageName이 egovframework.let.* 형식이 아니면 경로 오계산 발생 — 조기 실패 처리
        String pkgSub = extractPkgSub(pv.packageName());
        List<CrudLayerDefinition> layers = CrudLayerDefinition.forViewType(resolvedViewType).stream()
                .filter(layer -> resolvedViewType != CrudViewType.THYMELEAF
                        || resolvedLayoutMode == CrudLayoutMode.CREATE
                        || !CrudLayerDefinition.isLayoutLayer(layer.layerKey()))
                .toList();
        int layerCount = layers.size();

        sb.append("[생성 지시]\n");
        sb.append("위 스키마와 플레이스홀더 값을 바탕으로 ").append(layerCount).append("개 레이어 소스를 직접 작성하고,\n");
        sb.append("각 파일을 saveGeneratedCode(filePath, code)로 저장하세요.\n");
        sb.append("출력 경로: ").append(outputPath).append("\n\n");

        int step = 1;
        for (CrudLayerDefinition layer : layers) {
            String fileName = CrudLayerDefinition.resolveFileName(
                    layer.layerKey(), pv.domain(), layer.fileNameSuffix());
            String subPath  = layer.resolveSubPath(pkgSub, pv.domainLc());
            sb.append(String.format("  Step %2d: saveGeneratedCode(\"%s/%s%s\", code)\n",
                    step++, outputPath, subPath, fileName));
        }

        sb.append("\n").append(promptBuilder.postGeneration(outputPath, tableName, domain, packageName, pv.domainLc(), layerCount + "개 파일"));

        log.info("CRUD 프롬프트 빌드 완료: table={}, domain={}", tableName, domain);
        return sb.toString();
    }

    private void appendRegisteredRole(StringBuilder sb, CrudProgramMetadata metadata, String role, String label) {
        String path = metadata.registeredPath(role);
        if (path != null) {
            sb.append("    - ").append(label).append(": ").append(path).append("\n");
        }
    }

    private void appendViewTypeInstruction(
            StringBuilder sb,
            CrudViewType viewType,
            CrudLayoutMode layoutMode,
            String layoutView,
            String breadcrumbView) {
        if (viewType == CrudViewType.THYMELEAF) {
            sb.append("[화면 템플릿 지시]\n");
            sb.append("- JSP 파일은 생성하지 말고 Thymeleaf HTML 파일을 생성하세요.\n");
            if (layoutMode == CrudLayoutMode.CREATE) {
                sb.append("- 공통 레이아웃 파일 5종도 함께 생성하세요.\n");
                sb.append("  경로: src/main/resources/templates/layout/{default,gnb,lnb,breadcrumb,footer}.html\n");
                sb.append("  default.html은 th:replace로 partial을 조합하고, 화면 템플릿은 breadcrumb partial을 재사용하세요.\n");
            } else if (layoutMode == CrudLayoutMode.NONE) {
                sb.append("- 공통 레이아웃을 사용하지 않습니다. layout 파일 유무와 무관하게 동작하는 독립 화면을 생성하세요.\n");
            } else {
                sb.append("- 공통 레이아웃 파일은 생성하지 말고 기존 파일을 재사용하세요.\n");
                sb.append("  layout이 없다면 먼저 generateThymeleafLayout(outputPath=..., layoutBasePath=\"layout\")를 실행해야 합니다.\n");
            }
            sb.append("- Controller는 lnbTitle, lnbMenus, breadcrumbs를 직접 하드코딩하지 마세요.\n");
            sb.append("  generateThymeleafLayout()가 생성한 EgovGnbMenuInterceptor가 LETTNMENUINFO와 LETTNPROGRMLIST를 조인해 현재 요청 URL 기준으로 메뉴/브레드크럼을 주입합니다.\n");
            sb.append("  Controller는 메뉴 미등록 초기 상태를 위한 currentMenuId fallback만 설정하세요.\n");
            sb.append("- 화면 파일(목록/상세/등록/수정) 경로: src/main/resources/templates/{{DOMAIN_LC}}/Egov{{DOMAIN}}*.html\n");
            if (layoutMode == CrudLayoutMode.NONE) {
                sb.append("- layout:decorate 속성과 xmlns:layout 선언을 사용하지 마세요. <html><head>...</head><body>...</body></html> 구조를 화면 파일 안에 완결된 형태로 작성하세요.\n");
                sb.append("- <head>에 <meta charset=\"UTF-8\">, viewport 메타, th:href=\"@{/resources/css/styles.css}\" link를 직접 포함하세요.\n");
                sb.append("- </body> 직전에 th:src=\"@{/resources/js/krds.min.js}\" script를 직접 포함하세요.\n");
                sb.append("- breadcrumb partial 참조(th:replace=\"~{... :: breadcrumb}\")를 포함하지 마세요.\n");
                sb.append("- 콘텐츠는 layout:fragment 없이 <body> 아래에 직접 작성하세요.\n");
            } else {
                sb.append("- 각 화면 파일 최상단에 layout:decorate=\"~{").append(layoutView).append("}\" 선언 필수.\n");
                sb.append("- breadcrumb partial은 th:replace=\"~{").append(breadcrumbView).append(" :: breadcrumb}\"로 참조하세요.\n");
                sb.append("- 콘텐츠는 <th:block layout:fragment=\"content\"> 블록으로 감싸세요.\n");
            }
            sb.append("- JSTL, form taglib, <c:url>, <ui:pagination>은 사용하지 마세요.\n");
            sb.append("- Thymeleaf 문법 th:*, @{...}, ${...}, *{...}를 사용하세요.\n");
            sb.append("- 정적 리소스 경로는 /resources/css/, /resources/js/ 형식을 유지하세요.\n");
            sb.append("  WAR는 webapp/resources/**, BOOT는 static/resources/** 에 파일이 생성되지만 URL은 /resources/** 로 동일하게 유지합니다.\n");
            sb.append("- 페이지네이션은 #numbers.sequence(paginationInfo.firstPageNoOnPageList, paginationInfo.lastPageNoOnPageList)로 생성하세요.\n");
            sb.append("- 목록 검색 폼은 method=\"get\"을 사용하세요.\n");
            sb.append("- 등록·수정·삭제 후에는 redirect: + RedirectAttributes.addFlashAttribute(\"message\", ...) PRG 패턴을 사용하세요.\n");
            sb.append("- 상단 flash 메시지 출력: th:if=\"${message}\" + role=\"alert\" 구조. krds-alert는 확인된 공식 클래스가 아니므로 확정 사용하지 마세요.\n\n");
        }
    }

    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchColumns(String database, String tableName) {
        return crudSchemaQueryService.fetchColumns(database, tableName);
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
              .append("</th><td><c:out value=\"${result.").append(field).append("}\"/></td></tr>\n");
        }
        return sb.toString();
    }

    private boolean isSensitiveDisplayColumn(Map<String, Object> column) {
        Object raw = column.get("COLUMN_NAME");
        if (raw == null) raw = column.get("column_name");
        String columnName = raw == null ? null : raw.toString();
        return SensitiveFieldPolicy.isSensitiveDisplayField(toCamelCase(columnName), columnName);
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
              .append("\" value=\"<c:out value='${").append(field).append("}'/>\"");
            if (colName.equals(pkColumn)) sb.append(" readonly=\"readonly\"");
            sb.append("/>\n                            </td></tr>\n");
        }
        return sb.toString();
    }

    private String buildCommonCodeSection(List<Map<String, Object>> columns) {
        // LETTCCMMNCODE 전체를 1회만 조회하여 Map으로 캐싱 (N회 쿼리 → 1회)
        Map<String, String> codeIdMap;
        try {
            codeIdMap = jdbcTemplate.queryForList(
                "SELECT CODE_ID, CODE_ID_NM FROM LETTCCMMNCODE"
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

    /**
     * packageName에서 CrudLayerDefinition {PKG} 플레이스홀더용 서브 경로를 추출한다.
     * CrudLayerDefinition 템플릿은 egovframework/let/{PKG}/... 고정이므로
     * egovframework.let.* 형식만 허용한다.
     */
    private static String extractPkgSub(String packageName) {
        if (packageName == null || !packageName.startsWith("egovframework.let.")) {
            throw new IllegalArgumentException(
                "packageName은 egovframework.let.* 형식이어야 합니다: " + packageName);
        }
        return packageName.replace("egovframework.let.", "").replace(".", "/");
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
        return tableName.replace("LETTN", "").replace("LETTS", "").replace("LETTC", "");
    }
}
