package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.ColumnMeta;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.util.CrudMappingUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DB 컬럼 메타데이터(List&lt;Map&gt;) → CrudTemplateModel 변환 팩토리.
 * CrudSchemaQueryService 가 조회한 rawColumns 를 FreeMarker 렌더링에 필요한
 * 타입 안전 모델로 변환한다.
 */
@Service
public class CrudModelFactory {

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
        // Map → ColumnMeta 변환
        List<ColumnMeta> columns = rawColumns.stream()
                .map(this::toColumnMeta)
                .toList();

        List<FieldModel> fields = columns.stream()
                .map(this::toFieldModel)
                .toList();

        // PK 필드 — 없으면 첫 번째 컬럼을 PK로 간주 (CrudPromptBuilderService 동일 처리)
        FieldModel pkField = fields.stream()
                .filter(FieldModel::pk)
                .findFirst()
                .orElse(fields.get(0));

        PkModel pk = new PkModel(pkField.columnName(), pkField.javaName(), pkField.javaType());

        List<FieldModel> nonPkFields = fields.stream()
                .filter(f -> !f.javaName().equals(pkField.javaName()))
                .toList();

        // jakartaValidation: "5.x" 또는 "latest" → true (CrudPromptBuilderService:118-119 동일)
        boolean jakartaValidation = egovVersion != null
                && (egovVersion.startsWith("5") || "latest".equalsIgnoreCase(egovVersion));

        String domainLc = domain.substring(0, 1).toLowerCase() + domain.substring(1);
        String domainKr = CrudMappingUtils.extractKoreanName(tableName);

        // urlPrefix: "egovframework.let.emp" → "/emp/employer"
        String urlPrefix = "/" + packageName.replace("egovframework.let.", "")
                                            .replace(".", "/")
                           + "/" + domainLc;

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
                fields,
                nonPkFields
        );
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
