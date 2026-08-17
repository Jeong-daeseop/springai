package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.service.designsystem.ResolvedComponentRegistryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FigmaScreenExportSsotBundleTest {

    @Test
    void ssotDownloadUsesExactRegistryV3AndAddsEvidenceWithoutLegacyFallback() {
        FigmaScreenSpecRepository screens = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository legacyRegistries = mock(ComponentRegistryRepository.class);
        ComponentRegistrySnapshotV3Repository registriesV3 = mock(ComponentRegistrySnapshotV3Repository.class);
        ScreenPatternRepository patterns = mock(ScreenPatternRepository.class);
        VariantRuleSetRepository rules = mock(VariantRuleSetRepository.class);
        ResolvedComponentRegistryService resolver = mock(ResolvedComponentRegistryService.class);
        FigmaScreenSpec spec = spec();
        DesignSystemProfile profile = profile();
        ComponentRegistry legacy = new ComponentRegistry("krds", "2.0.0", "registry-1", null, Map.of());
        ComponentRegistrySnapshotV3 registryV3 = mock(ComponentRegistrySnapshotV3.class);
        ResolvedComponentRegistry resolved = new ResolvedComponentRegistry(
                "2.0.0", "a".repeat(64), "krds", "2.0.0", "registry-1", "b".repeat(64), Map.of());
        ScreenPatternDefinition pattern = new ScreenPatternDefinition(ScreenPattern.CRUD_LIST, "1.0.0", List.of());
        VariantRuleSet ruleSet = new VariantRuleSet(
                "rules", "2.0.0", "krds", "registry-1", VariantRuleSet.Status.PUBLISHED,
                List.of(new VariantRule("primary", 100, SemanticRole.ACTION_PRIMARY,
                        Map.of("pattern", "crud.list"), Map.of("type", "Primary"))));

        when(screens.findLatest("qna-list")).thenReturn(Optional.of(spec));
        when(profiles.findVersion("krds", "2.0.0")).thenReturn(Optional.of(profile));
        when(legacyRegistries.findVersion("krds", "registry-1")).thenReturn(Optional.of(legacy));
        when(registriesV3.findVersion("krds", "registry-1")).thenReturn(Optional.of(registryV3));
        when(resolver.resolve(registryV3, java.util.Set.of("krds.button"))).thenReturn(resolved);
        when(patterns.findVersion(ScreenPattern.CRUD_LIST, "1.0.0")).thenReturn(Optional.of(pattern));
        when(rules.findPublishedVersion("krds", "registry-1", "2.0.0")).thenReturn(Optional.of(ruleSet));

        FigmaScreenExportService service = service(
                screens, profiles, legacyRegistries, registriesV3, patterns, rules, resolver);

        assertThat(service.findLatestSsotBundle("qna-list").metadata().hasSsotEvidence()).isTrue();
    }

    @Test
    void missingRegistryV3FailsClosed() {
        FigmaScreenSpecRepository screens = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository legacyRegistries = mock(ComponentRegistryRepository.class);
        ComponentRegistrySnapshotV3Repository registriesV3 = mock(ComponentRegistrySnapshotV3Repository.class);
        when(screens.findLatest("qna-list")).thenReturn(Optional.of(spec()));
        when(profiles.findVersion("krds", "2.0.0")).thenReturn(Optional.of(profile()));
        when(legacyRegistries.findVersion("krds", "registry-1"))
                .thenReturn(Optional.of(new ComponentRegistry("krds", "2.0.0", "registry-1", null, Map.of())));
        when(registriesV3.findVersion("krds", "registry-1")).thenReturn(Optional.empty());

        FigmaScreenExportService service = service(screens, profiles, legacyRegistries, registriesV3,
                mock(ScreenPatternRepository.class), mock(VariantRuleSetRepository.class),
                mock(ResolvedComponentRegistryService.class));

        assertThatThrownBy(() -> service.findLatestSsotBundle("qna-list"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGISTRY_V3_NOT_FOUND");
    }

    private FigmaScreenExportService service(FigmaScreenSpecRepository screens,
            DesignSystemProfileRepository profiles, ComponentRegistryRepository legacyRegistries,
            ComponentRegistrySnapshotV3Repository registriesV3, ScreenPatternRepository patterns,
            VariantRuleSetRepository rules, ResolvedComponentRegistryService resolver) {
        return new FigmaScreenExportService(
                mock(ScreenSpecRepository.class), mock(FigmaScreenBuilderRegistry.class),
                mock(FigmaScreenTypeResolver.class), mock(LogicalNodeIdFactory.class),
                mock(FigmaScreenSpecValidator.class), profiles, legacyRegistries, screens,
                new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(new ObjectMapper()),
                null, null, null, patterns, rules, registriesV3, resolver);
    }

    private FigmaScreenSpec spec() {
        FigmaNodeSpec button = new FigmaNodeSpec(
                "qna-list/root/button", FigmaNodeSpec.NodeType.COMPONENT, "krds.button", Map.of(), List.of());
        FigmaNodeSpec content = new FigmaNodeSpec(
                "qna-list/root", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of(button));
        return new FigmaScreenSpec(
                "qna-list", 1, "qna", 1, FigmaScreenType.LIST, LayoutPattern.STANDARD,
                "Q&A 목록", "/qna", "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "2.0.0", "registry-1"), content, List.of(),
                ScreenPattern.CRUD_LIST, "1.0.0", "2.0.0", "2.1.0");
    }

    private DesignSystemProfile profile() {
        return new DesignSystemProfile(
                "krds", "KRDS", "2.0.0", "registry-1", null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
    }
}
