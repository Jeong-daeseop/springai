package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignCodeComponentMappingDiffServiceTest {

    private final DesignCodeComponentMappingDiffService service =
            new DesignCodeComponentMappingDiffService();

    @Test
    void 기준버전이없으면초기생성Diff다() {
        DesignCodeComponentMappingDiffService.MappingDiff diff = service.compare(null, base());

        assertThat(diff.initialCreation()).isTrue();
        assertThat(diff.contentChanged()).isTrue();
        assertThat(diff.changes()).singleElement().satisfies(change -> {
            assertThat(change.area()).isEqualTo(DesignCodeComponentMappingDiffService.Area.MAPPING);
            assertThat(change.changeType()).isEqualTo(DesignCodeComponentMappingDiffService.ChangeType.ADDED);
        });
    }

    @Test
    void propertySlotRenderer변경을결정적순서와Breaking표시로계산한다() {
        DesignCodeComponentMapping candidate = new DesignCodeComponentMapping(
                "map-button", "2.0", DesignCodeComponentMapping.Status.REVIEW_REQUIRED,
                "b".repeat(64), "krds.button", "BUTTON_SET", "fragments/button :: actionButton",
                List.of(
                        property("Label", "text", Map.of(), true, null),
                        property("Disabled", "disabled", Map.of("true", true), false, false)),
                List.of(new DesignCodeComponentMapping.SlotMapping("Content", "body")),
                Map.of("label", "확인"), List.of("thymeleaf-krds"), "revision-3", null, null);

        DesignCodeComponentMappingDiffService.MappingDiff diff = service.compare(base(), candidate);

        assertThat(diff.initialCreation()).isFalse();
        assertThat(diff.contentChanged()).isTrue();
        assertThat(diff.hasBreakingChanges()).isTrue();
        assertThat(diff.changes())
                .extracting(change -> change.area() + ":" + change.target() + ":" + change.changeType())
                .containsExactly(
                        "MAPPING:thymeleafFragment:MODIFIED",
                        "MAPPING:sourceRevision:MODIFIED",
                        "PROPERTY:Label:MODIFIED",
                        "PROPERTY:Style:REMOVED",
                        "PROPERTY:Disabled:ADDED",
                        "SLOT:Content:MODIFIED",
                        "RENDERER_PROFILE:react:REMOVED");
    }

    @Test
    void default와Fallback만바뀐Property는비Breaking변경이다() {
        DesignCodeComponentMapping before = mapping("1.0", "a".repeat(64),
                List.of(property("Label", "label", Map.of(), true, "확인")));
        DesignCodeComponentMapping after = mapping("1.1", "b".repeat(64),
                List.of(new DesignCodeComponentMapping.PropertyMapping(
                        "Label", "label", Map.of(), true, "저장", "취소")));

        DesignCodeComponentMappingDiffService.MappingDiff diff = service.compare(before, after);

        assertThat(diff.hasBreakingChanges()).isFalse();
        assertThat(diff.changes()).singleElement().satisfies(change -> {
            assertThat(change.changedFields()).containsExactly("defaultValue", "fallbackValue");
            assertThat(change.breaking()).isFalse();
        });
    }

    private DesignCodeComponentMapping base() {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED,
                "a".repeat(64), "krds.button", "BUTTON_SET", "fragments/button :: button",
                List.of(
                        property("Label", "label", Map.of(), true, null),
                        property("Style", "variant", Map.of("Primary", "primary"), false, null)),
                List.of(new DesignCodeComponentMapping.SlotMapping("Content", "content")),
                Map.of("label", "확인"), List.of("thymeleaf-krds", "react"), "revision-2",
                "reviewer", Instant.parse("2026-08-23T01:00:00Z"));
    }

    private DesignCodeComponentMapping mapping(
            String version, String hash,
            List<DesignCodeComponentMapping.PropertyMapping> properties) {
        return new DesignCodeComponentMapping(
                "map-button", version, DesignCodeComponentMapping.Status.REVIEW_REQUIRED, hash,
                "krds.button", "BUTTON_SET", "fragments/button :: button", properties, List.of(),
                null, List.of("thymeleaf-krds"), "revision-1", null, null);
    }

    private DesignCodeComponentMapping.PropertyMapping property(
            String figma, String parameter, Map<String, Object> values,
            boolean required, Object defaultValue) {
        return new DesignCodeComponentMapping.PropertyMapping(
                figma, parameter, values, required, defaultValue, null);
    }
}
