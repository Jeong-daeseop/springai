package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.ColumnMeta;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.GenerationQueryContract;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import com.krdevops.springai.policy.SensitiveFieldPolicy;
import com.krdevops.springai.policy.UiFieldRolePolicy;
import com.krdevops.springai.util.CrudMappingUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DB 컬럼 메타데이터(List&lt;Map&gt;) → CrudTemplateModel 변환 팩토리.
 * CrudSchemaQueryService 가 조회한 rawColumns 를 FreeMarker 렌더링에 필요한
 * 타입 안전 모델로 변환한다.
 */
@Service
public class CrudModelFactory {

    private final GenerationQueryContractFactory queryContractFactory;

    public CrudModelFactory() {
        this(new GenerationQueryContractFactory());
    }

    @Autowired
    public CrudModelFactory(GenerationQueryContractFactory queryContractFactory) {
        this.queryContractFactory = queryContractFactory;
    }

    /**
     * eGovFrame 표준 감사(audit) 컬럼 — 서버가 값을 채우므로 등록/수정 폼에서
     * 사용자가 직접 입력하지 않도록 formFields 에서 제외한다.
     */
    private static final Set<String> SYSTEM_MANAGED_FIELDS = Set.of(
            "frstRegistPnttm", "frstRegisterId", "lastUpdtPnttm", "lastUpdusrId"
    );

    /**
     * DB·Controller·VO 기반 업무 계약을 변경하지 않고 승인된 디자인 입력만 별도 영역에 연결한다.
     */
    public CrudTemplateModel withDesignComponents(
            CrudTemplateModel model, List<DesignComponentRenderInput> designComponents) {
        if (model == null) throw new IllegalArgumentException("model은 필수입니다.");
        return new CrudTemplateModel(
                model.packageName(), model.domain(), model.domainLc(), model.domainKr(),
                model.tableName(), model.urlPrefix(), model.date(), model.egovVersion(),
                model.jakartaValidation(), model.pk(), model.pkFields(), model.fields(),
                model.listFields(), model.nonPkFields(), model.formFields(), model.route(),
                model.queryContract(), model.detailFields(), model.layoutDensity(),
                model.formColumnLayout(), model.actionPlacement(), model.searchPanelPlacement(),
                designComponents);
    }

    /**
     * rawColumns 를 기반으로 FreeMarker 렌더링용 CrudTemplateModel 을 생성한다.
     *
     * @param tableName   DB 테이블명
     * @param domain      도메인명 (PascalCase, 예: Employer)
     * @param packageName 패키지명 (예: egovframework.let.emp)
     * @param egovVersion eGovFrame 버전 (예: "5.0", "4.3", "latest")
     * @param rawColumns  CrudSchemaQueryService.fetchColumns() 반환값
     * @return CrudTemplateModel
     * @throws java.util.NoSuchElementException rawColumns 가 비어 있어 PK 를 찾을 수 없는 경우
     */
    public CrudTemplateModel fromSchema(String tableName, String domain,
                                        String packageName, String egovVersion,
                                        List<Map<String, Object>> rawColumns) {
        return fromSchema(tableName, domain, packageName, egovVersion, rawColumns,
                CrudProgramMetadata.fallback(null), CrudViewType.JSP,
                ScreenSubsetMode.LIST_ONLY, null);
    }

    /**
     * DB({@code LETTNPROGRMLIST}) 조회 메타데이터를 반영해 CrudTemplateModel을 생성한다.
     * metadata가 각 화면 role에 대한 등록 URL을 찾지 못했으면 canonical URL만 사용한다.
     *
     * @param metadata {@link CrudProgramMetadataService#resolve} 결과 (fallback 허용)
     */
    public CrudTemplateModel fromSchema(String tableName, String domain,
                                        String packageName, String egovVersion,
                                        List<Map<String, Object>> rawColumns,
                                        CrudProgramMetadata metadata) {
        return fromSchema(tableName, domain, packageName, egovVersion, rawColumns, metadata,
                CrudViewType.JSP, ScreenSubsetMode.LIST_ONLY, null);
    }

    public CrudTemplateModel fromSchema(String tableName, String domain,
                                        String packageName, String egovVersion,
                                        List<Map<String, Object>> rawColumns,
                                        CrudProgramMetadata metadata,
                                        ScreenSpecification screenSpecification) {
        return fromSchema(tableName, domain, packageName, egovVersion, rawColumns, metadata,
                CrudViewType.JSP, ScreenSubsetMode.LIST_ONLY, screenSpecification);
    }

    public CrudTemplateModel fromSchema(String tableName, String domain,
                                        String packageName, String egovVersion,
                                        List<Map<String, Object>> rawColumns,
                                        CrudProgramMetadata metadata,
                                        CrudViewType viewType,
                                        ScreenSubsetMode subsetMode,
                                        ScreenSpecification screenSpecification) {
        CrudViewType resolvedViewType = viewType == null ? CrudViewType.JSP : viewType;
        ScreenSubsetMode resolvedSubsetMode = subsetMode == null
                ? (resolvedViewType == CrudViewType.THYMELEAF
                        ? ScreenSubsetMode.LIST_AND_DETAIL : ScreenSubsetMode.LIST_ONLY)
                : subsetMode;
        // Map → ColumnMeta 변환
        List<ColumnMeta> columns = rawColumns.stream()
                .map(this::toColumnMeta)
                .toList();

        List<FieldModel> fields = columns.stream()
                .map(this::toFieldModel)
                .toList();

        // PK 필드 — 복합키(예: NTT_ID+BBS_ID)를 모두 인식한다.
        // 없으면 첫 번째 컬럼을 PK로 간주 (CrudPromptBuilderService 동일 처리)
        List<FieldModel> pkFields = fields.stream().filter(FieldModel::pk).toList();
        FieldModel pkField = !pkFields.isEmpty() ? pkFields.get(0) : fields.get(0);
        List<FieldModel> effectivePkFields = !pkFields.isEmpty() ? pkFields : List.of(pkField);

        PkModel pk = new PkModel(pkField.columnName(), pkField.javaName(), pkField.javaType());

        java.util.Set<String> pkJavaNames = effectivePkFields.stream()
                .map(FieldModel::javaName)
                .collect(java.util.stream.Collectors.toSet());
        List<FieldModel> nonPkFields = fields.stream()
                .filter(f -> !pkJavaNames.contains(f.javaName()))
                .toList();
        List<FieldModel> formFields = buildFormFields(nonPkFields, screenSpecification);
        formFields = queryContractFactory.applyLabelOverrides(formFields, screenSpecification, "regist");
        ScreenSpecification listSpec = resolvedSubsetMode == ScreenSubsetMode.NONE ? null : screenSpecification;
        ScreenSpecification detailSpec = resolvedSubsetMode == ScreenSubsetMode.LIST_AND_DETAIL
                ? screenSpecification : null;
        List<FieldModel> listFields = buildListFields(fields, effectivePkFields, listSpec);
        listFields = queryContractFactory.applyLabelOverrides(listFields, listSpec, "list");
        List<FieldModel> detailFields = buildDetailFields(
                fields, effectivePkFields, resolvedSubsetMode, screenSpecification);
        detailFields = queryContractFactory.applyLabelOverrides(detailFields, detailSpec, "detail");
        GenerationQueryContract queryContract = queryContractFactory.create(screenSpecification, fields);
        if (resolvedSubsetMode != ScreenSubsetMode.NONE && !queryContract.displayFields().isEmpty()) {
            java.util.ArrayList<FieldModel> extended = new java.util.ArrayList<>(listFields);
            queryContract.displayFields().forEach(field -> addIfAbsent(extended, field));
            listFields = List.copyOf(extended);
        }

        // jakartaValidation: "5.x" 또는 "latest" → true (CrudPromptBuilderService:118-119 동일)
        boolean jakartaValidation = egovVersion != null
                && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));

        String domainLc = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        // 우선순위: 명시 programKoreanName > DB PROGRM_KOREAN_NM(둘 다 metadata.programKoreanName()에
        // 이미 반영됨, CrudProgramMetadataService 참고) > 테이블명 기반 fallback.
        // 화면 종류 접미어("목록"/"상세" 등)는 미리 제거한다 — 그러지 않으면 화면 제목에서
        // 템플릿이 다시 붙이는 접미어와 중복된다("게시판 목록조회" + "목록" → "게시판 목록조회 목록").
        String metadataKoreanName = metadata == null ? null : metadata.programKoreanName();
        String domainKr = metadataKoreanName != null
                ? CrudMappingUtils.stripScreenTypeSuffix(metadataKoreanName)
                : CrudMappingUtils.extractKoreanName(tableName);

        // urlPrefix: "egovframework.let.emp" → "/emp/employer"
        String urlPrefix = "/" + packageName.replace("egovframework.let.", "")
                                            .replace(".", "/")
                           + "/" + domainLc;
        CrudRouteModel route = buildRoute(urlPrefix, metadata);

        return new CrudTemplateModel(
                packageName,
                domain,
                domainLc,
                domainKr,
                tableName,
                urlPrefix,
                LocalDate.now().toString(),
                egovVersion,
                jakartaValidation,
                pk,
                effectivePkFields,
                fields,
                listFields,
                nonPkFields,
                formFields,
                route,
                queryContract,
                detailFields,
                screenSpecification == null
                        ? LayoutDensity.STANDARD : screenSpecification.layoutDensity(),
                screenSpecification == null
                        ? FormColumnLayout.SINGLE_COLUMN : screenSpecification.formColumnLayout(),
                screenSpecification == null
                        ? ActionPlacement.TOP_RIGHT : screenSpecification.actionPlacement(),
                screenSpecification == null
                        ? SearchPanelPlacement.ABOVE_TABLE : screenSpecification.searchPanelPlacement()
        );
    }

    private CrudRouteModel buildRoute(String urlPrefix, CrudProgramMetadata metadata) {
        CrudProgramMetadata md = metadata == null ? CrudProgramMetadata.fallback(null) : metadata;
        return new CrudRouteModel(
                urlPrefix + "List.do",       md.registeredPath(CrudProgramMetadataService.ROLE_LIST),
                urlPrefix + "Detail.do",     md.registeredPath(CrudProgramMetadataService.ROLE_DETAIL),
                urlPrefix + "RegistView.do", md.registeredPath(CrudProgramMetadataService.ROLE_REGIST_VIEW),
                urlPrefix + "Regist.do",     md.registeredPath(CrudProgramMetadataService.ROLE_REGIST),
                urlPrefix + "UpdtView.do",   md.registeredPath(CrudProgramMetadataService.ROLE_UPDT_VIEW),
                urlPrefix + "Updt.do",       md.registeredPath(CrudProgramMetadataService.ROLE_UPDT),
                urlPrefix + "Delete.do",     md.registeredPath(CrudProgramMetadataService.ROLE_DELETE),
                md.registeredListUrl()
        );
    }

    /**
     * screenSpecification의 regist 페이지가 selectionSource() != DEFAULT로 명시한 COLUMN 소스
     * 필드만 신뢰해 등록/수정 폼 필드를 구성한다. 스펙이 없거나 유효한 선택이 없으면 기존 동작
     * (PK·SYSTEM_MANAGED_FIELDS만 제외한 전체 컬럼)으로 폴백한다.
     */
    private List<FieldModel> buildFormFields(List<FieldModel> nonPkFields, ScreenSpecification screenSpecification) {
        if (screenSpecification != null) {
            List<String> columns = screenSpecification.pages().stream()
                    .filter(page -> "regist".equalsIgnoreCase(page.id()))
                    .filter(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                    .flatMap(page -> page.fields().stream())
                    .filter(field -> field.visible() && field.source() != null)
                    .filter(field -> field.source().type() == FieldSourceType.COLUMN)
                    .map(field -> field.source().column())
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!columns.isEmpty()) {
                var selected = new java.util.ArrayList<FieldModel>();
                for (String column : columns) {
                    nonPkFields.stream()
                            .filter(field -> field.columnName().equalsIgnoreCase(column))
                            .filter(field -> !SYSTEM_MANAGED_FIELDS.contains(field.javaName()))
                            .findFirst()
                            .ifPresent(field -> addIfAbsent(selected, field));
                }
                if (!selected.isEmpty()) {
                    return List.copyOf(selected);
                }
            }
        }
        return nonPkFields.stream()
                .filter(f -> !SYSTEM_MANAGED_FIELDS.contains(f.javaName()))
                .toList();
    }

    private List<FieldModel> buildListFields(List<FieldModel> fields, FieldModel pkField) {
        return buildListFields(fields, List.of(pkField), null);
    }

    private List<FieldModel> buildListFields(
            List<FieldModel> fields,
            List<FieldModel> pkFields,
            ScreenSpecification screenSpecification) {
        FieldModel pkField = pkFields.get(0);
        if (screenSpecification != null) {
            List<String> columns = screenSpecification.pages().stream()
                    .filter(page -> "list".equalsIgnoreCase(page.id()))
                    .filter(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                    .flatMap(page -> page.fields().stream())
                    .filter(field -> field.visible() && field.source() != null)
                    .filter(field -> field.source().type() == FieldSourceType.COLUMN)
                    .map(field -> field.source().column())
                    .filter(java.util.Objects::nonNull)
                    .toList();
            java.util.ArrayList<FieldModel> selected = new java.util.ArrayList<>();
            pkFields.forEach(field -> addIfAbsent(selected, field));
            for (String column : columns) {
                fields.stream().filter(field -> field.columnName().equalsIgnoreCase(column))
                        .filter(field -> !isSensitiveListField(field))
                        .findFirst().ifPresent(field -> addIfAbsent(selected, field));
                if (selected.size() >= 6) break;
            }
            if (selected.size() > 1 || columns.stream().anyMatch(pkField.columnName()::equalsIgnoreCase)) {
                return List.copyOf(selected);
            }
        }
        List<String> preferred = List.of(
                "userNm", "emplNo", "ofcpsNm", "emailAdres", "mbtlnum",
                "orgnztId", "emplyrSttusCode", "brthdy", "sexdstnCode"
        );
        java.util.ArrayList<FieldModel> selected = new java.util.ArrayList<>();
        pkFields.forEach(field -> addIfAbsent(selected, field));

        for (var role : UiFieldRolePolicy.LIST_ROLE_PRIORITY) {
            for (String columnName : UiFieldRolePolicy.candidateColumns(role)) {
                fields.stream()
                        .filter(field -> field.columnName().equalsIgnoreCase(columnName))
                        .filter(field -> !isSensitiveListField(field))
                        .findFirst()
                        .ifPresent(field -> addIfAbsent(selected, field));
                if (selected.size() >= 6) return List.copyOf(selected);
            }
        }

        for (String javaName : preferred) {
            fields.stream()
                    .filter(f -> f.javaName().equals(javaName))
                    .filter(f -> !f.javaName().equals(pkField.javaName()))
                    .filter(f -> !isSensitiveListField(f))
                    .findFirst()
                    .ifPresent(f -> addIfAbsent(selected, f));
            if (selected.size() >= 6) {
                return List.copyOf(selected);
            }
        }

        for (FieldModel field : fields) {
            if (selected.size() >= 6) {
                break;
            }
            if (!isSensitiveListField(field)) {
                addIfAbsent(selected, field);
            }
        }
        return List.copyOf(selected);
    }

    private List<FieldModel> buildDetailFields(
            List<FieldModel> fields,
            List<FieldModel> pkFields,
            ScreenSubsetMode subsetMode,
            ScreenSpecification screenSpecification) {
        if (subsetMode != ScreenSubsetMode.LIST_AND_DETAIL || screenSpecification == null) {
            return SensitiveFieldPolicy.filterDisplayFields(fields);
        }
        List<String> columns = screenSpecification.pages().stream()
                .filter(page -> "detail".equalsIgnoreCase(page.id()))
                .filter(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                .flatMap(page -> page.fields().stream())
                .filter(binding -> binding.visible() && binding.source() != null)
                .filter(binding -> binding.source().type() == FieldSourceType.COLUMN)
                .map(binding -> binding.source().column())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (columns.isEmpty()) return SensitiveFieldPolicy.filterDisplayFields(fields);

        java.util.ArrayList<FieldModel> selected = new java.util.ArrayList<>();
        pkFields.stream()
                .filter(field -> !SensitiveFieldPolicy.isSensitiveDisplayField(field))
                .forEach(field -> addIfAbsent(selected, field));
        for (String column : columns) {
            fields.stream()
                    .filter(field -> field.columnName().equalsIgnoreCase(column))
                    .filter(field -> !SensitiveFieldPolicy.isSensitiveDisplayField(field))
                    .findFirst()
                    .ifPresent(field -> addIfAbsent(selected, field));
        }
        return List.copyOf(selected);
    }

    private static void addIfAbsent(List<FieldModel> selected, FieldModel field) {
        if (selected.stream().noneMatch(f -> f.javaName().equals(field.javaName()))) {
            selected.add(field);
        }
    }

    private boolean isSensitiveListField(FieldModel field) {
        String name = field.javaName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("password")
                || name.contains("ihid")
                || name.contains("esntl")
                || name.contains("cert")
                || name.contains("dn")
                || name.contains("lock")
                || name.contains("uniq")
                || name.contains("secret");
    }

    /**
     * rawColumn Map → ColumnMeta record.
     * Map 키는 CrudSchemaQueryService.fetchColumns() SQL 결과 기준.
     */
    private ColumnMeta toColumnMeta(Map<String, Object> row) {
        Object len = row.get("CHARACTER_MAXIMUM_LENGTH");
        return new ColumnMeta(
                (String) row.get("COLUMN_NAME"),
                (String) row.get("DATA_TYPE"),
                len != null ? ((Number) len).intValue() : 0,
                !"NO".equals(row.get("IS_NULLABLE")),
                "PRI".equals(row.get("COLUMN_KEY")),
                row.get("COLUMN_COMMENT") != null ? (String) row.get("COLUMN_COMMENT") : ""
        );
    }

    /**
     * ColumnMeta → FieldModel record.
     * 타입 변환·jdbcType·stringType 판단은 CrudMappingUtils 에 위임한다.
     */
    private FieldModel toFieldModel(ColumnMeta col) {
        String javaType = CrudMappingUtils.mapJavaType(col.dataType(), col.columnSize());
        Integer maxLength = "varchar".equalsIgnoreCase(col.dataType()) ? col.columnSize() : null;

        return new FieldModel(
                col.columnName(),
                CrudMappingUtils.toCamelCase(col.columnName()),
                javaType,
                col.remarks(),
                col.pk(),
                !col.nullable(),
                "String".equals(javaType),
                maxLength,
                CrudMappingUtils.mapJdbcType(col.dataType())
        );
    }
}
