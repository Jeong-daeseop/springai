package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.thymeleaf.CompanyDesignTokenResolver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ComponentRegistry(Figma "Publish" Variable/Component)에서 DESIGN.md YAML frontmatter를
 * 내보내는 로직 검증. raw hex/px 값이 아니라 CSS 변수 이름만 담기는지, 그리고
 * {@link CompanyDesignTokenResolver}가 다루지 않는 {@code components}도 별도로 반영되는지가
 * 핵심 계약이다.
 */
class ComponentRegistryToDesignMdExporterTest {

    private final CompanyDesignTokenResolver tokenResolver = mock(CompanyDesignTokenResolver.class);
    private final DesignSystemQueryService designSystemQueryService = mock(DesignSystemQueryService.class);
    private final ComponentRegistryToDesignMdExporter exporter =
            new ComponentRegistryToDesignMdExporter(tokenResolver, designSystemQueryService);

    @Test
    void export_success_rendersYamlFrontmatterWithCssVariableNamesOnly() {
        ResolvedDesignTokens tokens = new ResolvedDesignTokens(
                "profile-1", "1", null,
                Map.of("primary", "--krds-color-light-primary-60"),
                Map.of("bodyFont", "--krds-font-family-base"),
                Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        when(tokenResolver.resolve(eq("profile-1"), isNull()))
                .thenReturn(ThymeleafGenerationStageResult.success(tokens, List.of()));
        when(designSystemQueryService.findLatestRegistry("profile-1"))
                .thenThrow(new IllegalArgumentException("ComponentRegistry를 찾을 수 없습니다: profile-1"));

        String designMd = exporter.export("profile-1");

        assertThat(designMd)
                .startsWith("---\n")
                .contains("schemaVersion: '1.0'")
                .contains("colors:")
                .contains("primary: --krds-color-light-primary-60")
                .contains("typography:")
                .contains("bodyFont: --krds-font-family-base")
                .contains("profile-1");
    }

    @Test
    void export_includesComponentsSection_forCurrentActiveComponentsOnly() {
        ResolvedDesignTokens tokens = new ResolvedDesignTokens(
                "profile-1", "1", null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        when(tokenResolver.resolve(eq("profile-1"), isNull()))
                .thenReturn(ThymeleafGenerationStageResult.success(tokens, List.of()));

        ComponentRegistryEntry button = new ComponentRegistryEntry(
                "button-set-key", "button",
                ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT,
                null, List.of("button"), Map.of(), Map.of(),
                Set.of(SemanticRole.ACTION_PRIMARY, SemanticRole.ACTION_SECONDARY),
                Set.of(), Map.of(), Set.of(), "KrdsButton", null, "2.1.0");
        ComponentRegistryEntry deprecated = new ComponentRegistryEntry(
                "old-set-key", "old",
                ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                null, List.of(), Map.of(), Map.of(),
                Set.of(), Set.of(), Map.of(), Set.of(), "KrdsOld", null, "2.1.0");
        ComponentRegistry registry = new ComponentRegistry(
                "profile-1", "1", "2.1.0", new ComponentRegistry.LibraryRef("figma-key", "KRDS Library"),
                Map.of("krds.button", button, "krds.old", deprecated), Map.of());
        when(designSystemQueryService.findLatestRegistry("profile-1")).thenReturn(registry);

        String designMd = exporter.export("profile-1");

        assertThat(designMd)
                .contains("components:")
                .contains("krds.button:")
                .contains("codeComponent: KrdsButton")
                .contains("action.primary")
                .contains("action.secondary")
                // DEPRECATED 컴포넌트는 생성에 쓸 수 없으므로 반영하지 않는다.
                .doesNotContain("krds.old")
                .doesNotContain("KrdsOld");
    }

    @Test
    void export_resolveFails_returnsMinimalNonThrowingDocument() {
        GenerationIssue fatal = new GenerationIssue(
                "DESIGN_SYSTEM_PROFILE_NOT_FOUND", GenerationIssue.Severity.FATAL, "R6-056",
                null, "DesignSystemProfile을 찾을 수 없습니다: missing", null);
        when(tokenResolver.resolve(eq("missing"), isNull()))
                .thenReturn(ThymeleafGenerationStageResult.failure(List.of(fatal)));

        String designMd = exporter.export("missing");

        assertThat(designMd)
                .startsWith("---\n")
                .contains("schemaVersion")
                .contains("missing");
    }
}
