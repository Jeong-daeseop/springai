package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.board.BoardDisplayModel;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardRouteModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.FieldSourceType;
import com.krdevops.springai.model.design.GenerationQueryContract;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.policy.SensitiveFieldPolicy;
import com.krdevops.springai.util.CrudMappingUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 게시판(BBS) 스키마 → {@link BoardTemplateModel} 변환 팩토리.
 *
 * <p>컬럼 변환 로직은 {@code CrudModelFactory} 와 동일하며, 게시판 전용으로
 * 복합 PK(BBS_ID, NTT_ID) 탐색·첨부파일 판단·목록/폼/검색 필드 선별을 추가한다.
 */
@Service
public class BoardModelFactory {

    private final GenerationQueryContractFactory queryContractFactory;

    public BoardModelFactory() {
        this(new GenerationQueryContractFactory());
    }

    @Autowired
    public BoardModelFactory(GenerationQueryContractFactory queryContractFactory) {
        this.queryContractFactory = queryContractFactory;
    }

    public BoardTemplateModel fromSchemas(
            String mainTable, String masterTable, String useTable,
            String fileDetailTable,
            String domain, String packageName, String egovVersion,
            Map<String, List<Map<String, Object>>> schemas) {
        return fromSchemas(mainTable, masterTable, useTable, fileDetailTable,
                domain, packageName, egovVersion, schemas, BoardProgramMetadata.fallback(null), null);
    }

    public BoardTemplateModel fromSchemas(
            String mainTable, String masterTable, String useTable,
            String fileDetailTable,
            String domain, String packageName, String egovVersion,
            Map<String, List<Map<String, Object>>> schemas,
            BoardProgramMetadata metadata) {
        return fromSchemas(mainTable, masterTable, useTable, fileDetailTable,
                domain, packageName, egovVersion, schemas, metadata, null);
    }

    public BoardTemplateModel fromSchemas(
            String mainTable, String masterTable, String useTable,
            String fileDetailTable,
            String domain, String packageName, String egovVersion,
            Map<String, List<Map<String, Object>>> schemas,
            BoardProgramMetadata metadata,
            ScreenSpecification screenSpecification) {

        // mainTable 컬럼 → FieldModel 변환
        List<FieldModel> fields = schemas.get("main").stream()
                .map(this::toFieldModel).toList();

        // 복합 PK: BBS_ID, NTT_ID 탐색
        FieldModel bbsId = fields.stream()
                .filter(f -> f.columnName().equalsIgnoreCase("BBS_ID"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("BBS_ID 컬럼 없음: " + mainTable));
        FieldModel nttId = fields.stream()
                .filter(f -> f.columnName().equalsIgnoreCase("NTT_ID"))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("NTT_ID 컬럼 없음: " + mainTable));

        // 첨부파일: ATCH_FILE_ID 컬럼 존재 여부
        FieldModel atchFileId = fields.stream()
                .filter(f -> f.columnName().equalsIgnoreCase("ATCH_FILE_ID"))
                .findFirst().orElse(null);
        boolean hasFile = atchFileId != null && !schemas.getOrDefault("file", List.of()).isEmpty();
        String resolvedUseTable = !schemas.getOrDefault("use", List.of()).isEmpty() ? useTable : null;
        String resolvedFileDetailTable = !schemas.getOrDefault("fileDetail", List.of()).isEmpty()
                ? fileDetailTable : null;

        // listFields: screenSpecification의 list 페이지가 명시적으로 선택한 COLUMN을 우선 사용한다
        // (CrudModelFactory.buildListFields와 동일 계약). 스펙이 없거나 유효한 선택이 없으면
        // 기존 하드코딩 우선순위(공지여부, 게시글번호, 제목, 작성자명, 등록일시)로 폴백한다.
        List<FieldModel> listFields = buildListFieldsFromSpec(fields, bbsId, nttId, screenSpecification);
        if (listFields == null) {
            List<String> preferredList = List.of("noticeAt", "nttId", "nttSj", "ntcrNm", "frstRegistPnttm");
            listFields = buildPreferredFields(fields, preferredList, 6);
        }
        listFields = queryContractFactory.applyLabelOverrides(listFields, screenSpecification, "list");
        GenerationQueryContract queryContract = queryContractFactory.create(
                screenSpecification, fields, "b", java.util.Set.of("b", "m"));
        if (!queryContract.displayFields().isEmpty()) {
            var extended = new java.util.ArrayList<>(listFields);
            queryContract.displayFields().stream()
                    .filter(field -> extended.stream().noneMatch(existing -> existing.javaName().equals(field.javaName())))
                    .forEach(extended::add);
            listFields = List.copyOf(extended);
        }

        // insertFields: rdcnt·lastUpdtPnttm 등 DB 자동관리 컬럼만 제외 (bbsId·nttId 포함)
        List<String> excludeInsert = List.of("rdcnt", "lastUpdtPnttm");
        List<FieldModel> insertFields = fields.stream()
                .filter(f -> excludeInsert.stream().noneMatch(ex -> f.javaName().equalsIgnoreCase(ex)))
                .toList();

        // detailFields: screenSpecification의 detail 페이지가 명시적으로 선택한 COLUMN을 우선 사용.
        // 스펙이 없거나 유효한 선택이 없으면 기존 동작(민감 필드만 제외한 전체 컬럼)으로 폴백한다.
        List<FieldModel> detailFields = buildDetailFields(fields, bbsId, nttId, screenSpecification);
        detailFields = queryContractFactory.applyLabelOverrides(detailFields, screenSpecification, "detail");

        // formFields: screenSpecification의 regist 페이지가 명시적으로 선택한 COLUMN을 우선 사용.
        // 스펙이 없거나 유효한 선택이 없으면 기존 동작(BBS_ID/NTT_ID 등 내부관리 컬럼만 제외)으로 폴백한다.
        List<FieldModel> formFields = buildFormFields(fields, screenSpecification);
        formFields = queryContractFactory.applyLabelOverrides(formFields, screenSpecification, "regist");

        // searchFields: 제목, 내용, 작성자
        List<String> preferredSearch = List.of("nttSj", "nttCn", "ntcrNm");
        List<FieldModel> searchFields = buildPreferredFields(fields, preferredSearch, 3);

        // NOTICE_AT 컬럼 존재 여부 — ORDER BY 조건부 생성
        boolean noticeAtExists = fields.stream()
                .anyMatch(f -> f.columnName().equalsIgnoreCase("NOTICE_AT"));

        boolean jakartaValidation = egovVersion != null
                && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));

        String domainLc = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        String pkgSub = packageName.replace("egovframework.let.", "").replace(".", "/");
        String urlPrefix = "/" + pkgSub + "/" + domainLc;

        String fallbackName = CrudMappingUtils.extractKoreanName(mainTable);
        String displayName = metadata != null && metadata.programKoreanName() != null
                ? metadata.programKoreanName() : fallbackName;
        BoardDisplayModel display = new BoardDisplayModel(
                metadata == null ? null : metadata.programFileName(), displayName,
                metadata == null ? null : metadata.upperMenuName());
        BoardRouteModel route = new BoardRouteModel(urlPrefix,
                metadata == null ? null : metadata.registeredUrl(),
                metadata == null ? null : metadata.registeredPath(),
                metadata == null ? null : metadata.defaultBbsId());

        return new BoardTemplateModel(
                packageName, domain, domainLc,
                fallbackName,
                mainTable, masterTable, resolvedUseTable,
                urlPrefix,
                java.time.LocalDate.now().toString(),
                egovVersion, jakartaValidation,
                bbsId, nttId,
                hasFile, atchFileId, resolvedFileDetailTable,
                fields, listFields, detailFields, insertFields, formFields, searchFields, noticeAtExists,
                display, route, queryContract
        );
    }

    // CrudModelFactory 와 동일한 변환 로직
    private FieldModel toFieldModel(Map<String, Object> row) {
        Object len = row.get("CHARACTER_MAXIMUM_LENGTH");
        String dataType = (String) row.get("DATA_TYPE");
        String columnName = (String) row.get("COLUMN_NAME");
        boolean nullable = !"NO".equals(row.get("IS_NULLABLE"));
        boolean pk = "PRI".equals(row.get("COLUMN_KEY"));
        String comment = row.get("COLUMN_COMMENT") != null ? (String) row.get("COLUMN_COMMENT") : "";
        int columnSize = len != null ? ((Number) len).intValue() : 0;

        String javaType = CrudMappingUtils.mapJavaType(dataType, columnSize);
        Integer maxLength = "varchar".equalsIgnoreCase(dataType) ? columnSize : null;

        return new FieldModel(
                columnName,
                CrudMappingUtils.toCamelCase(columnName),
                javaType, comment, pk, !nullable,
                "String".equals(javaType), maxLength,
                CrudMappingUtils.mapJdbcType(dataType)
        );
    }

    /**
     * CrudModelFactory.buildListFields와 동일한 계약: screenSpecification의 list 페이지가
     * selectionSource() != DEFAULT로 명시한 COLUMN 소스 필드만 신뢰하고, 복합 PK(BBS_ID, NTT_ID)를
     * 항상 포함해 최대 6개로 제한한다. 스펙이 없거나 유효한 선택이 없으면 null을 돌려줘 호출부가
     * 기존 preferredList 폴백을 쓰게 한다.
     */
    private List<FieldModel> buildListFieldsFromSpec(
            List<FieldModel> fields, FieldModel bbsId, FieldModel nttId,
            ScreenSpecification screenSpecification) {
        if (screenSpecification == null) {
            return null;
        }
        List<String> columns = screenSpecification.pages().stream()
                .filter(page -> "list".equalsIgnoreCase(page.id()))
                .filter(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.visible() && field.source() != null)
                .filter(field -> field.source().type() == FieldSourceType.COLUMN)
                .map(field -> field.source().column())
                .filter(java.util.Objects::nonNull)
                .toList();
        if (columns.isEmpty()) {
            return null;
        }

        var selected = new java.util.ArrayList<FieldModel>();
        addFieldIfAbsent(selected, bbsId);
        addFieldIfAbsent(selected, nttId);
        for (String column : columns) {
            fields.stream().filter(field -> field.columnName().equalsIgnoreCase(column))
                    .findFirst().ifPresent(field -> addFieldIfAbsent(selected, field));
            if (selected.size() >= 6) break;
        }
        return selected.size() > 2 ? List.copyOf(selected) : null;
    }

    /**
     * CrudModelFactory.buildDetailFields와 동일한 계약: screenSpecification의 detail 페이지가
     * selectionSource() != DEFAULT로 명시한 COLUMN 소스 필드만 신뢰하고, 복합 PK(BBS_ID, NTT_ID)를
     * 항상 포함한다. 스펙이 없거나 유효한 선택이 없으면 민감 필드(비밀번호 등)만 제외한 전체
     * 컬럼을 반환한다(기존 동작과 동일, SensitiveFieldPolicy만 새로 적용).
     */
    private List<FieldModel> buildDetailFields(
            List<FieldModel> fields, FieldModel bbsId, FieldModel nttId,
            ScreenSpecification screenSpecification) {
        List<String> columns = screenSpecification == null ? List.of()
                : screenSpecification.pages().stream()
                        .filter(page -> "detail".equalsIgnoreCase(page.id()))
                        .filter(page -> page.selectionSource() != FieldSelectionSource.DEFAULT)
                        .flatMap(page -> page.fields().stream())
                        .filter(field -> field.visible() && field.source() != null)
                        .filter(field -> field.source().type() == FieldSourceType.COLUMN)
                        .map(field -> field.source().column())
                        .filter(java.util.Objects::nonNull)
                        .toList();
        if (columns.isEmpty()) {
            return SensitiveFieldPolicy.filterDisplayFields(fields);
        }

        var selected = new java.util.ArrayList<FieldModel>();
        java.util.stream.Stream.of(bbsId, nttId)
                .filter(field -> !SensitiveFieldPolicy.isSensitiveDisplayField(field))
                .forEach(field -> addFieldIfAbsent(selected, field));
        for (String column : columns) {
            fields.stream()
                    .filter(field -> field.columnName().equalsIgnoreCase(column))
                    .filter(field -> !SensitiveFieldPolicy.isSensitiveDisplayField(field))
                    .findFirst()
                    .ifPresent(field -> addFieldIfAbsent(selected, field));
        }
        return List.copyOf(selected);
    }

    /**
     * screenSpecification의 regist 페이지가 selectionSource() != DEFAULT로 명시한 COLUMN 소스
     * 필드만 신뢰해 등록/수정 폼 필드를 구성한다. 스펙이 없거나 유효한 선택이 없으면 기존 동작
     * (BBS_ID/NTT_ID/RDCNT 등 내부관리 컬럼만 제외한 전체 컬럼)으로 폴백한다.
     */
    private List<FieldModel> buildFormFields(List<FieldModel> fields, ScreenSpecification screenSpecification) {
        // frstRegistPnttm/frstRegisterId/lastUpdtPnttm/lastUpdusrId는 CrudModelFactory와 동일하게
        // eGovFrame 표준 감사(audit) 컬럼이라 화면 입력을 받지 않는다 — 값은 controller.java.ftl이
        // 서버측에서 채운다(사용자가 직접 타이핑하는 텍스트 필드로 노출하면 안 됨).
        List<String> excludeForm = List.of("bbsId", "nttId", "rdcnt", "answerAt", "answerLc", "sortOrdr",
                "frstRegistPnttm", "frstRegisterId", "lastUpdtPnttm", "lastUpdusrId");
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
                    fields.stream()
                            .filter(field -> field.columnName().equalsIgnoreCase(column))
                            .filter(field -> excludeForm.stream().noneMatch(ex -> field.javaName().equalsIgnoreCase(ex)))
                            .findFirst()
                            .ifPresent(field -> addFieldIfAbsent(selected, field));
                }
                if (!selected.isEmpty()) {
                    return List.copyOf(selected);
                }
            }
        }
        return fields.stream()
                .filter(f -> excludeForm.stream().noneMatch(ex -> f.javaName().equalsIgnoreCase(ex)))
                .toList();
    }

    private static void addFieldIfAbsent(List<FieldModel> selected, FieldModel field) {
        if (selected.stream().noneMatch(f -> f.javaName().equals(field.javaName()))) {
            selected.add(field);
        }
    }

    private List<FieldModel> buildPreferredFields(List<FieldModel> fields, List<String> preferred, int max) {
        var selected = new java.util.ArrayList<FieldModel>();
        for (String javaName : preferred) {
            fields.stream()
                    .filter(f -> f.javaName().equalsIgnoreCase(javaName))
                    .findFirst().ifPresent(selected::add);
            if (selected.size() >= max) return List.copyOf(selected);
        }
        for (FieldModel f : fields) {
            if (selected.size() >= max) break;
            if (selected.stream().noneMatch(s -> s.javaName().equals(f.javaName()))) {
                selected.add(f);
            }
        }
        return List.copyOf(selected);
    }
}
