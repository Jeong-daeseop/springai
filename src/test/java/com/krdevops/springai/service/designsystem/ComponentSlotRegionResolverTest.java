package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentSlotRegionResolverTest {

    private final ComponentSlotRegionResolver resolver = new ComponentSlotRegionResolver();

    @Test
    void figmaSlot을Mapping순서대로Fragment영역에투영한다() {
        ComponentSlotRegionResolver.Resolution result = resolver.requireResolved(
                mapping(DesignCodeComponentMapping.Status.APPROVED),
                Map.of("Leading icon", "icon-search", "Content", List.of("label-node")));

        assertThat(result.valid()).isTrue();
        assertThat(result.fragmentRegions()).containsExactly(
                Map.entry("leadingIcon", "icon-search"),
                Map.entry("content", List.of("label-node")));
        assertThat(result.consumedFigmaSlots()).containsExactly("Leading icon", "Content");
        assertThat(result.missingMappedSlots()).isEmpty();
    }

    @Test
    void 내용없는선언Slot은빈영역을만들지않고근거로보존한다() {
        ComponentSlotRegionResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED),
                Map.of("Content", List.of("label-node")));

        assertThat(result.valid()).isTrue();
        assertThat(result.fragmentRegions()).containsOnlyKeys("content");
        assertThat(result.missingMappedSlots()).containsExactly("Leading icon");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("MAPPED_SLOT_NOT_PRESENT");
            assertThat(issue.target()).isEqualTo("Leading icon");
        });
    }

    @Test
    void 매핑되지않은Slot은버리지않고경고근거로보존한다() {
        ComponentSlotRegionResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED),
                Map.of("Content", "본문", "Trailing badge", "NEW"));

        assertThat(result.valid()).isTrue();
        assertThat(result.unmappedFigmaSlots()).containsExactly("Trailing badge");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("UNMAPPED_FIGMA_SLOT");
            assertThat(issue.target()).isEqualTo("Trailing badge");
        });
    }

    @Test
    void 승인되지않은SlotMapping은Apply경계에서차단한다() {
        DesignCodeComponentMapping mapping = mapping(DesignCodeComponentMapping.Status.DRAFT);

        assertThat(resolver.resolve(mapping, Map.of("Content", "본문")).valid()).isFalse();
        assertThatThrownBy(() -> resolver.requireResolved(mapping, Map.of("Content", "본문")))
                .isInstanceOf(ComponentSlotRegionResolver.ComponentSlotResolutionException.class)
                .satisfies(exception -> assertThat(
                        ((ComponentSlotRegionResolver.ComponentSlotResolutionException) exception)
                                .resolution().issues())
                        .extracting(ComponentSlotRegionResolver.ResolutionIssue::code)
                        .contains("MAPPING_NOT_APPROVED"));
    }

    @Test
    void 입력과출력컬렉션은호출뒤변경의영향을받지않는다() {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("Content", List.of("label-node"));
        ComponentSlotRegionResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED), input);

        input.put("Content", "변경");

        assertThat(result.fragmentRegions().get("content")).isEqualTo(List.of("label-node"));
        assertThatThrownBy(() -> result.fragmentRegions().put("content", "수정"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private DesignCodeComponentMapping mapping(DesignCodeComponentMapping.Status status) {
        String approvedBy = status == DesignCodeComponentMapping.Status.APPROVED ? "reviewer" : null;
        Instant approvedAt = status == DesignCodeComponentMapping.Status.APPROVED
                ? Instant.parse("2026-08-23T01:00:00Z") : null;
        return new DesignCodeComponentMapping(
                "map-button", "1.0", status, "a".repeat(64), "button", "FIGMA_BUTTON",
                "fragments/button :: button", List.of(),
                List.of(
                        new DesignCodeComponentMapping.SlotMapping("Leading icon", "leadingIcon"),
                        new DesignCodeComponentMapping.SlotMapping("Content", "content")),
                null, List.of("thymeleaf-krds"), "figma-r1", approvedBy, approvedAt);
    }
}
