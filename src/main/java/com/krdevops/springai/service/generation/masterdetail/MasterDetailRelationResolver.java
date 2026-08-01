package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.util.CrudMappingUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Master PK와 Detail 컬럼의 관계를 결정한다. 기존 동작처럼 PK 이름을 기본 FK로 사용한다. */
@Component
public class MasterDetailRelationResolver {

    public Relation resolve(String masterPkColumn, List<Map<String, Object>> detailColumns) {
        String fkColumn = detailColumns.stream()
                .map(column -> (String) column.get("COLUMN_NAME"))
                .filter(masterPkColumn::equals)
                .findFirst()
                .orElse(masterPkColumn);
        return new Relation(fkColumn, CrudMappingUtils.toCamelCase(fkColumn));
    }

    public record Relation(String fkColumn, String fkField) {
    }
}
