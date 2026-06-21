package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.util.CrudMappingUtils;
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

    public BoardTemplateModel fromSchemas(
            String mainTable, String masterTable, String useTable,
            String fileDetailTable,
            String domain, String packageName, String egovVersion,
            Map<String, List<Map<String, Object>>> schemas) {

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
        boolean hasFile = atchFileId != null && schemas.containsKey("file");

        // listFields: 공지여부, 게시글번호, 제목, 작성자명, 등록일시 우선
        List<String> preferredList = List.of("noticeAt", "nttId", "nttSj", "ntcrNm", "frstRegistPnttm");
        List<FieldModel> listFields = buildPreferredFields(fields, preferredList, 6);

        // insertFields: rdcnt·lastUpdtPnttm 등 DB 자동관리 컬럼만 제외 (bbsId·nttId 포함)
        List<String> excludeInsert = List.of("rdcnt", "lastUpdtPnttm");
        List<FieldModel> insertFields = fields.stream()
                .filter(f -> excludeInsert.stream().noneMatch(ex -> f.javaName().equalsIgnoreCase(ex)))
                .toList();

        // formFields: BBS_ID, NTT_ID, 내부관리 컬럼 제외 (UI 표시/입력용)
        List<String> excludeForm = List.of("bbsId", "nttId", "rdcnt", "answerAt", "answerLc", "sortOrdr");
        List<FieldModel> formFields = fields.stream()
                .filter(f -> excludeForm.stream().noneMatch(ex -> f.javaName().equalsIgnoreCase(ex)))
                .toList();

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

        return new BoardTemplateModel(
                packageName, domain, domainLc,
                CrudMappingUtils.extractKoreanName(mainTable),
                mainTable, masterTable, useTable,
                urlPrefix,
                java.time.LocalDate.now().toString(),
                egovVersion, jakartaValidation,
                bbsId, nttId,
                hasFile, atchFileId, fileDetailTable,
                fields, listFields, insertFields, formFields, searchFields, noticeAtExists
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
