package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KrdsQnaFixtureBootstrapServiceTest {
    @Test
    void importsPublishedVersionAlignedProfileRegistryRulesPatternsInventoryAndSixScreens() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ComponentRegistryRepository registries = mock(ComponentRegistryRepository.class);
        ScreenPatternRepository patterns = mock(ScreenPatternRepository.class);
        VariantRuleSetRepository rules = mock(VariantRuleSetRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        FigmaScreenSpecRepository screens = mock(FigmaScreenSpecRepository.class);
        FigmaLibraryInventoryRepository inventories = mock(FigmaLibraryInventoryRepository.class);
        when(registries.findVersion("krds", "2.1.0")).thenReturn(Optional.empty());
        when(profiles.findVersion("krds", "2.0.0")).thenReturn(Optional.empty());
        when(inventories.findVersion("krds", "2.1.0",
                KrdsQnaFixtureBootstrapService.INVENTORY_VERSION)).thenReturn(Optional.empty());

        KrdsRuntimeContractImportService reader = new KrdsRuntimeContractImportService(
                registries, patterns, rules, mapper);
        KrdsQnaFixtureBootstrapService service = new KrdsQnaFixtureBootstrapService(
                reader, registries, patterns, rules, profiles, screens, inventories, mapper);

        var result = service.bootstrap();

        assertThat(result.screenIds()).containsExactlyInAnyOrder(
                "qna-list", "qna-create", "qna-detail",
                "qna-answer-list", "qna-answer-detail", "qna-answer-create");
        assertThat(result.patternCount()).isEqualTo(4);
        ArgumentCaptor<VariantRuleSet> ruleCaptor = ArgumentCaptor.forClass(VariantRuleSet.class);
        verify(rules).saveImmutable(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().status()).isEqualTo(VariantRuleSet.Status.PUBLISHED);
        ArgumentCaptor<FigmaScreenSpec> screenCaptor = ArgumentCaptor.forClass(FigmaScreenSpec.class);
        verify(screens, times(6)).save(screenCaptor.capture());
        assertThat(screenCaptor.getAllValues())
                .allSatisfy(screen -> {
                    assertThat(screen.status()).isEqualTo("APPROVED");
                    assertThat(screen.designSystem().profileVersion()).isEqualTo("2.0.0");
                    assertThat(screen.designSystem().registryVersion()).isEqualTo("2.1.0");
                    assertThat(screen.variantRuleSetVersion()).isEqualTo("2.0.0-candidate");
                    assertThat(screen.componentContractVersion()).isEqualTo("2.1.0");
                });
        FigmaScreenSpec list = screenCaptor.getAllValues().stream()
                .filter(screen -> screen.screenId().equals("qna-list"))
                .findFirst()
                .orElseThrow();
        assertThat(list.screenVersion()).isEqualTo(6);
        assertThat(list.content().properties())
                .containsEntry("layoutRecipe", "krds.listPage.v1")
                .containsEntry("contentMaxWidth", 1280)
                .containsEntry("sectionGap", 40);
        assertThat(list.content().children()).extracting(FigmaNodeSpec::type)
                .containsExactly("krds.pageHeader", "krds.searchPanel", "krds.dataTable",
                        "krds.pagination", "egov.actionArea");
        assertThat(list.content().children().get(1).properties())
                .containsEntry("componentMaxWidth", 960);
        FigmaNodeSpec table = list.content().children().get(2);
        assertThat(table.properties())
                .containsEntry("layoutRecipe", "krds.dataTable.v1")
                .containsEntry("columnCount", 6)
                .containsEntry("sampleRowCount", 3);
        assertThat(table.children()).hasSize(4)
                .allSatisfy(row -> assertThat(row.children()).hasSize(6));
        assertThat(table.children()).allSatisfy(row -> {
            assertThat(row.children().get(0).properties()).containsEntry("columnWidthPercent", 8);
            assertThat(row.children().get(1).properties()).containsEntry("columnWidthPercent", 32);
            assertThat(row.children().get(2).properties()).containsEntry("columnWidthPercent", 15);
        });
        FigmaNodeSpec actionArea = list.content().children().get(4);
        assertThat(actionArea.properties()).containsEntry("placement", "BOTTOM_RIGHT");
        assertThat(actionArea.children()).hasSize(1);
        verify(inventories).saveImmutable(any());
    }
}
