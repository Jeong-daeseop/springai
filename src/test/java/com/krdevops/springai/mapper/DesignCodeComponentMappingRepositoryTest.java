package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DesignCodeComponentMappingRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DesignCodeComponentMappingRepository repository =
            new DesignCodeComponentMappingRepository(jdbcTemplate, mapper);

    @Test
    void 버전별Json을조회하고RendererProfile까지필터링한다() throws Exception {
        DesignCodeComponentMapping mapping = approved();
        String json = mapper.writeValueAsString(mapping);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class),
                eq("button"), eq("FIGMA_BUTTON"))).thenReturn(List.of(json));

        assertThat(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .contains(mapping);
        assertThat(repository.findApproved("button", "FIGMA_BUTTON", "react"))
                .isEmpty();
    }

    @Test
    void immutableInsert에식별자버전상태와Hash를함께저장한다() {
        DesignCodeComponentMapping mapping = approved();

        repository.saveImmutable(mapping);

        verify(jdbcTemplate).update(anyString(),
                eq("map-button"), eq("1.0"), eq("APPROVED"), eq("button"),
                eq("FIGMA_BUTTON"), eq("fragments/button :: button"), eq("figma-r1"),
                eq("reviewer"), eq(mapping.approvedAt()), eq("a".repeat(64)), anyString());
    }

    private DesignCodeComponentMapping approved() {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED, "a".repeat(64),
                "button", "FIGMA_BUTTON", "fragments/button :: button",
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Size", "size", Map.of("Small", "sm"), true, "md", null)),
                List.of(new DesignCodeComponentMapping.SlotMapping("Leading icon", "leadingIcon")),
                Map.of("label", "확인"), List.of("thymeleaf-krds"), "figma-r1", "reviewer",
                Instant.parse("2026-08-23T01:00:00Z"));
    }
}
