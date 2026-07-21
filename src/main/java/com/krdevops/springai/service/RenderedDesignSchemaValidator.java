package com.krdevops.springai.service;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

/** classpath에 고정된 중립 JSON Schema를 사용한다. */
public final class RenderedDesignSchemaValidator {
    private final Schema schema;

    public RenderedDesignSchemaValidator() {
        var stream = RenderedDesignSchemaValidator.class.getResourceAsStream(
                "/contracts/rendered-design-document-v1.schema.json");
        if (stream == null) throw new IllegalStateException("RenderedDesignDocument Schema를 찾을 수 없습니다.");
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(stream);
    }

    public void validate(String json) {
        var errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("RenderedDesignDocument Schema 검증 실패: "
                    + errors.get(0).getMessage());
        }
    }
}
