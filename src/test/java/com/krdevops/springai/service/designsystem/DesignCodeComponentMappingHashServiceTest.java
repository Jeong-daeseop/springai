package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignCodeComponentMappingHashServiceTest {

    private final DesignCodeComponentMappingHashService service =
            new DesignCodeComponentMappingHashService(new ObjectMapper());

    @Test
    void version상태승인메타데이터가달라도의미Payload가같으면Hash가같다() {
        DesignCodeComponentMapping draft = mapping("1.0", DesignCodeComponentMapping.Status.DRAFT,
                null, null, Map.of("label", "확인"));
        DesignCodeComponentMapping approved = mapping("rollback-2.0",
                DesignCodeComponentMapping.Status.APPROVED, "operator",
                Instant.parse("2026-08-23T01:00:00Z"), Map.of("label", "확인"));

        assertThat(service.compute(draft)).isEqualTo(service.compute(approved));
    }

    @Test
    void fixtureMap입력순서가달라도Hash는결정적이다() {
        LinkedHashMap<String, Object> left = new LinkedHashMap<>();
        left.put("label", "확인");
        left.put("disabled", false);
        LinkedHashMap<String, Object> right = new LinkedHashMap<>();
        right.put("disabled", false);
        right.put("label", "확인");

        assertThat(service.compute(mapping("1.0", DesignCodeComponentMapping.Status.DRAFT,
                null, null, left))).isEqualTo(service.compute(mapping(
                        "1.0", DesignCodeComponentMapping.Status.DRAFT, null, null, right)));
    }

    private DesignCodeComponentMapping mapping(
            String version, DesignCodeComponentMapping.Status status,
            String actor, Instant approvedAt, Map<String, Object> fixture) {
        return new DesignCodeComponentMapping(
                "map-button", version, status, "0".repeat(64), "krds.button", "BUTTON_SET",
                "fragments/button :: button",
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Label", "label", Map.of(), true, null, null)),
                List.of(), fixture, List.of("thymeleaf-krds"), "revision-1", actor, approvedAt);
    }
}
