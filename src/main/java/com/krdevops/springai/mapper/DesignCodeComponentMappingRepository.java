package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** DesignCodeComponentMapping을 mappingId/version 단위 불변 Snapshot으로 저장한다. */
@Repository
public class DesignCodeComponentMappingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DesignCodeComponentMappingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void saveImmutable(DesignCodeComponentMapping mapping) {
        jdbcTemplate.update("""
            INSERT INTO AI_DESIGN_CODE_COMPONENT_MAPPING
              (MAPPING_ID, MAPPING_VERSION, MAPPING_STATUS, LOGICAL_TYPE,
               FIGMA_COMPONENT_SET_KEY, THYMELEAF_FRAGMENT, SOURCE_REVISION,
               APPROVED_BY, APPROVED_AT, CONTENT_HASH, MAPPING_JSON)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, mapping.mappingId(), mapping.version(), mapping.status().name(),
                mapping.logicalType(), mapping.figmaComponentSetKey(), mapping.thymeleafFragment(),
                mapping.sourceRevision(), mapping.approvedBy(), mapping.approvedAt(),
                mapping.contentHash(), toJson(mapping));
    }

    public Optional<DesignCodeComponentMapping> findVersion(String mappingId, String version) {
        return first(jdbcTemplate.queryForList("""
            SELECT MAPPING_JSON FROM AI_DESIGN_CODE_COMPONENT_MAPPING
             WHERE MAPPING_ID = ? AND MAPPING_VERSION = ?
            """, String.class, mappingId, version));
    }

    public Optional<DesignCodeComponentMapping> findLatest(String mappingId) {
        return first(jdbcTemplate.queryForList("""
            SELECT MAPPING_JSON FROM AI_DESIGN_CODE_COMPONENT_MAPPING
             WHERE MAPPING_ID = ? ORDER BY CREATED_AT DESC, MAPPING_VERSION DESC LIMIT 1
            """, String.class, mappingId));
    }

    public Optional<DesignCodeComponentMapping> findLatestApproved(String logicalType) {
        return first(jdbcTemplate.queryForList("""
            SELECT MAPPING_JSON FROM AI_DESIGN_CODE_COMPONENT_MAPPING
             WHERE LOGICAL_TYPE = ? AND MAPPING_STATUS = 'APPROVED'
             ORDER BY APPROVED_AT DESC, CREATED_AT DESC LIMIT 1
            """, String.class, logicalType));
    }

    public Optional<DesignCodeComponentMapping> findApproved(
            String logicalType, String figmaComponentSetKey, String rendererProfile) {
        return jdbcTemplate.queryForList("""
            SELECT MAPPING_JSON FROM AI_DESIGN_CODE_COMPONENT_MAPPING
             WHERE LOGICAL_TYPE = ? AND FIGMA_COMPONENT_SET_KEY = ?
               AND MAPPING_STATUS = 'APPROVED'
             ORDER BY APPROVED_AT DESC, CREATED_AT DESC
            """, String.class, logicalType, figmaComponentSetKey).stream()
                .map(this::fromJson)
                .filter(mapping -> mapping.supportedRendererProfiles().contains(rendererProfile))
                .findFirst();
    }

    private Optional<DesignCodeComponentMapping> first(List<String> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromJson(rows.get(0)));
    }

    private String toJson(DesignCodeComponentMapping mapping) {
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception exception) {
            throw new IllegalStateException("DesignCodeComponentMapping JSON 직렬화 실패", exception);
        }
    }

    private DesignCodeComponentMapping fromJson(String json) {
        try {
            return objectMapper.readValue(json, DesignCodeComponentMapping.class);
        } catch (Exception exception) {
            throw new IllegalStateException("DesignCodeComponentMapping JSON 역직렬화 실패", exception);
        }
    }
}
