package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ThymeleafKrdsComponentMappingSeederTest {

    private final DesignCodeComponentMappingHashService hashService =
            new DesignCodeComponentMappingHashService(new ObjectMapper());
    private final ThymeleafKrdsComponentMappingSeeder seeder = new ThymeleafKrdsComponentMappingSeeder(
            mock(DesignCodeComponentMappingRepository.class), hashService);

    private final ComponentFixtureModelAdapter fixtureAdapter = new ComponentFixtureModelAdapter();
    private final DesignComponentRenderInputService renderInputService = new DesignComponentRenderInputService(
            mock(DesignCodeComponentMappingRepository.class),
            new ComponentPropertyParameterResolver(),
            new ComponentVariantValueResolver(),
            new ComponentSlotRegionResolver());

    @Test
    void 시드_6종이_생성되고_krds_논리키와_APPROVED_계약을_만족한다() {
        var mappings = seeder.mappings();

        assertThat(mappings).extracting(DesignCodeComponentMapping::logicalType)
                .containsExactlyInAnyOrder(
                        "button", "text-input", "select", "date-input", "data-table", "pagination");
        assertThat(mappings).allSatisfy(mapping -> {
            assertThat(mapping.status()).isEqualTo(DesignCodeComponentMapping.Status.APPROVED);
            assertThat(mapping.figmaComponentSetKey()).isEqualTo("krds:" + mapping.logicalType());
            assertThat(mapping.supportedRendererProfiles()).containsExactly("thymeleaf-krds");
            assertThat(mapping.thymeleafFragment()).matches("components/krds-[a-z-]+ :: [a-zA-Z]+");
            // contentHash가 payload와 자기정합
            assertThat(mapping.contentHash()).isEqualTo(hashService.compute(mapping));
        });
    }

    @Test
    void 각_시드_매핑이_Fixture_Adapter와_RenderInput_해석을_통과한다() {
        for (DesignCodeComponentMapping mapping : seeder.mappings()) {
            var adaptation = fixtureAdapter.adapt(mapping);
            assertThat(adaptation.issues())
                    .noneMatch(issue -> issue.severity() == ComponentFixtureModelAdapter.Severity.ERROR);
            assertThat(adaptation.fixture()).isNotNull();

            Map<String, Object> figmaProperties = adaptation.fixture().figmaProperties();
            DesignComponentRenderInput input = renderInputService.resolve(
                    mapping, "thymeleaf-krds", figmaProperties, Map.of());

            assertThat(input.thymeleafFragment()).isEqualTo(mapping.thymeleafFragment());
            assertThat(input.logicalType()).isEqualTo(mapping.logicalType());
            assertThat(input.rendererProfile()).isEqualTo("thymeleaf-krds");
        }
    }
}
