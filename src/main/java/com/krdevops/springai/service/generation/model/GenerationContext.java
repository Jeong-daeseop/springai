package com.krdevops.springai.service.generation.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기능(CRUD/게시판/마스터-디테일)에 공통인 생성 문맥. 명세서 §10.2.
 *
 * <p>공용 Processor는 이 record의 명시 필드만 읽는다. 기능 전용 Processor는 {@link #attributes}에
 * 담긴 값(예: CRUD의 {@code CrudTemplateModel})을 {@link #attribute(String)}로 꺼내 쓴다.
 */
public record GenerationContext(
        String feature,
        String database,
        String tableName,
        String domain,
        String packageName,
        String outputPath,
        String egovVersion,
        String viewType,
        Map<String, Object> attributes
) {
    public GenerationContext {
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }
}
