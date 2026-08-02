package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistry.LibraryRef;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableRegistryEntry;
import com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * R6-056: CompanyDesignTokenResolver 테스트.
 *
 * <p>회사 표준 Design Token을 로드하고 해석한다.
 */
class CompanyDesignTokenResolverTest {

    private CompanyDesignTokenResolver resolver;
    private DesignSystemQueryService queryService;
    private GenerationIssueFactory issueFactory;

    @BeforeEach
    void setUp() {
        queryService = Mockito.mock(DesignSystemQueryService.class);
        issueFactory = new GenerationIssueFactory();
        resolver = new CompanyDesignTokenResolver(queryService, issueFactory);
    }

    @Test
    void resolveSuccessfullyLoadsProfileAndRegistry() {
        // Arrange
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds-default",
                "Design System",
                "1.0.0",
                "1.0.0",
                "figma-key",
                DesignSystemProfile.Status.APPROVED,
                Map.of(),
                Map.of()
        );

        VariableRegistryEntry colorVar = new VariableRegistryEntry(
                "primary-60",
                "Primary 60%",
                "colors",
                "Colors",
                "color",
                null
        );

        ComponentRegistry registry = new ComponentRegistry(
                "krds-default",
                "1.0.0",
                "1.0.0",
                new LibraryRef("figma-key", "KRDS Library"),
                Map.of(),
                Map.of("colors.primary.60", colorVar)
        );

        when(queryService.findLatestProfile("krds-default")).thenReturn(profile);
        when(queryService.findLatestRegistry("krds-default")).thenReturn(registry);

        // Act
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = resolver.resolve("krds-default", null);

        // Assert
        assertThat(result.successful()).isTrue();
        assertThat(result.value()).isNotNull();
        assertThat(result.value().profileId()).isEqualTo("krds-default");
        assertThat(result.value().colorTokens()).isNotEmpty();
    }

    @Test
    void resolveHandlesProfileNotFound() {
        // Arrange
        when(queryService.findLatestProfile(anyString()))
                .thenThrow(new IllegalArgumentException("Profile not found"));

        // Act
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = resolver.resolve("unknown-profile", null);

        // Assert
        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).isNotEmpty();
        assertThat(result.issues().stream()
                .anyMatch(i -> i.severity() == GenerationIssue.Severity.FATAL))
                .isTrue();
    }

    @Test
    void resolveHandlesRegistryNotFound() {
        // Arrange
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds-default",
                "Design System",
                "1.0.0",
                "1.0.0",
                "figma-key",
                DesignSystemProfile.Status.APPROVED,
                Map.of(),
                Map.of()
        );

        when(queryService.findLatestProfile("krds-default")).thenReturn(profile);
        when(queryService.findLatestRegistry(anyString()))
                .thenThrow(new IllegalArgumentException("Registry not found"));

        // Act
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = resolver.resolve("krds-default", null);

        // Assert
        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).isNotEmpty();
        assertThat(result.issues().stream()
                .anyMatch(i -> i.severity() == GenerationIssue.Severity.FATAL))
                .isTrue();
    }

    @Test
    void resolveTokensAreMapedByType() {
        // Arrange
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds-default",
                "Design System",
                "1.0.0",
                "1.0.0",
                "figma-key",
                DesignSystemProfile.Status.APPROVED,
                Map.of(),
                Map.of()
        );

        VariableRegistryEntry colorVar = new VariableRegistryEntry(
                "primary-60",
                "Primary 60%",
                "colors",
                "Colors",
                "color",
                null
        );

        VariableRegistryEntry spacingVar = new VariableRegistryEntry(
                "sm",
                "Small Spacing",
                "spacing",
                "Spacing",
                "spacing",
                null
        );

        ComponentRegistry registry = new ComponentRegistry(
                "krds-default",
                "1.0.0",
                "1.0.0",
                new LibraryRef("figma-key", "KRDS Library"),
                Map.of(),
                Map.of(
                        "colors.primary.60", colorVar,
                        "spacing.sm", spacingVar
                )
        );

        when(queryService.findLatestProfile("krds-default")).thenReturn(profile);
        when(queryService.findLatestRegistry("krds-default")).thenReturn(registry);

        // Act
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = resolver.resolve("krds-default", null);

        // Assert
        assertThat(result.successful()).isTrue();
        ResolvedDesignTokens tokens = result.value();
        assertThat(tokens.colorTokens()).containsKey("colors.primary.60");
        assertThat(tokens.spacingTokens()).containsKey("spacing.sm");
    }

    @Test
    void resolveWithEmptyRegistry() {
        // Arrange
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds-default",
                "Design System",
                "1.0.0",
                "1.0.0",
                "figma-key",
                DesignSystemProfile.Status.APPROVED,
                Map.of(),
                Map.of()
        );

        ComponentRegistry registry = new ComponentRegistry(
                "krds-default",
                "1.0.0",
                "1.0.0",
                new LibraryRef("figma-key", "KRDS Library"),
                Map.of(),
                Map.of()
        );

        when(queryService.findLatestProfile("krds-default")).thenReturn(profile);
        when(queryService.findLatestRegistry("krds-default")).thenReturn(registry);

        // Act
        ThymeleafGenerationStageResult<ResolvedDesignTokens> result = resolver.resolve("krds-default", null);

        // Assert
        assertThat(result.successful()).isTrue();
        assertThat(result.value().colorTokens()).isEmpty();
        assertThat(result.value().spacingTokens()).isEmpty();
    }
}
