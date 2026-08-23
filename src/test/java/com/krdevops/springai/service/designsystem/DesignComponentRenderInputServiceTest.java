package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignComponentRenderInputServiceTest {

    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);
    private final DesignComponentRenderInputService service = new DesignComponentRenderInputService(
            repository, new ComponentPropertyParameterResolver(),
            new ComponentVariantValueResolver(), new ComponentSlotRegionResolver());

    @Test
    void 승인Mapping의PropertyVariantSlot을Renderer입력으로조합한다() {
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.of(mapping()));

        var result = service.resolve(
                "button", "FIGMA_BUTTON", "thymeleaf-krds",
                Map.of("Style", "Primary", "Disabled", true, "Label", "저장"),
                Map.of("Leading icon", "save"));

        assertThat(result.mappingId()).isEqualTo("map-button");
        assertThat(result.thymeleafFragment()).isEqualTo("fragments/button :: button");
        assertThat(result.fragmentParameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "variant", "primary", "disabled", true, "label", "저장"));
        assertThat(result.fragmentRegions()).containsOnlyKeys("leadingIcon")
                .containsEntry("leadingIcon", "save");
        assertThat(result.rendererProfile()).isEqualTo("thymeleaf-krds");
    }

    @Test
    void 승인Mapping이없으면Renderer입력을만들지않는다() {
        when(repository.findApproved("button", "MISSING", "thymeleaf-krds"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(
                "button", "MISSING", "thymeleaf-krds", Map.of(), Map.of()))
                .isInstanceOf(DesignComponentRenderInputService
                        .ApprovedComponentMappingNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    private DesignCodeComponentMapping mapping() {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED,
                "a".repeat(64), "button", "FIGMA_BUTTON", "fragments/button :: button",
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Style", "variant", Map.of("Primary", "primary"), true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Disabled", "disabled", Map.of("true", true, "false", false),
                                false, false, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null)),
                List.of(new DesignCodeComponentMapping.SlotMapping(
                        "Leading icon", "leadingIcon")),
                null, List.of("thymeleaf-krds"), "figma-r1", "reviewer",
                Instant.parse("2026-08-23T01:00:00Z"));
    }
}
