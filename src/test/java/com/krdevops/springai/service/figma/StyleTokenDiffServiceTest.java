package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R5-045: 참조 Style Token 후보와 운영 Profile Token 차이 계산 검증. */
class StyleTokenDiffServiceTest {

    private final StyleTokenDiffService service = new StyleTokenDiffService();

    @Test
    void newColorTokenNotInProfileIsClassifiedAsNewCandidate() {
        var candidates = new FigmaStyleExtractor.DesignTokenExtraction(
                List.of(new FigmaStyleExtractor.ColorToken("Primary/60", "key1", null, "primary-60")),
                List.of(), List.of(), List.of());
        DesignSystemProfile profile = profileWithVariables(Map.of());

        StyleTokenDiffService.StyleTokenDiffResult result = service.diff(candidates, profile);

        assertThat(result.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.tokenName()).isEqualTo("primary-60");
            assertThat(entry.status()).isEqualTo(StyleTokenDiffService.StyleTokenDiffStatus.NEW_CANDIDATE);
        });
        assertThat(result.newCandidateCount()).isEqualTo(1);
    }

    @Test
    void colorTokenAlreadyBoundInProfileIsMatched() {
        var candidates = new FigmaStyleExtractor.DesignTokenExtraction(
                List.of(new FigmaStyleExtractor.ColorToken("Primary/60", "key1", null, "primary-60")),
                List.of(), List.of(), List.of());
        DesignSystemProfile profile = profileWithVariables(Map.of(
                "primary-60", new VariableBinding("var-1", "Primitives", ComponentBinding.BindingStatus.BOUND)));

        StyleTokenDiffService.StyleTokenDiffResult result = service.diff(candidates, profile);

        assertThat(result.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo(StyleTokenDiffService.StyleTokenDiffStatus.MATCHED);
            assertThat(entry.variableId()).isEqualTo("var-1");
        });
        assertThat(result.newCandidateCount()).isZero();
    }

    @Test
    void tokenDefinedButNotBoundYetIsUnboundInProfile() {
        var candidates = new FigmaStyleExtractor.DesignTokenExtraction(
                List.of(new FigmaStyleExtractor.ColorToken("Primary/60", "key1", null, "primary-60")),
                List.of(), List.of(), List.of());
        DesignSystemProfile profile = profileWithVariables(Map.of(
                "primary-60", new VariableBinding(null, null, ComponentBinding.BindingStatus.UNBOUND)));

        StyleTokenDiffService.StyleTokenDiffResult result = service.diff(candidates, profile);

        assertThat(result.entries()).singleElement().satisfies(entry ->
                assertThat(entry.status()).isEqualTo(StyleTokenDiffService.StyleTokenDiffStatus.UNBOUND_IN_PROFILE));
    }

    @Test
    void typographySpacingAndRadiusCandidatesAreAllIncludedInTheDiff() {
        var candidates = new FigmaStyleExtractor.DesignTokenExtraction(
                List.of(),
                List.of(new FigmaStyleExtractor.TypographyToken("Body/16", "key2", null, "16px", "400")),
                List.of(new FigmaStyleExtractor.SpacingToken("Spacing/8", "key3", null, "8px")),
                List.of(new FigmaStyleExtractor.RadiusToken("Radius/4", "key4", null, "4px")));
        DesignSystemProfile profile = profileWithVariables(Map.of());

        StyleTokenDiffService.StyleTokenDiffResult result = service.diff(candidates, profile);

        assertThat(result.entries()).hasSize(3);
        assertThat(result.entries()).allSatisfy(entry ->
                assertThat(entry.status()).isEqualTo(StyleTokenDiffService.StyleTokenDiffStatus.NEW_CANDIDATE));
    }

    @Test
    void nullProfileTreatsEveryCandidateAsNew() {
        var candidates = new FigmaStyleExtractor.DesignTokenExtraction(
                List.of(new FigmaStyleExtractor.ColorToken("Primary/60", "key1", null, "primary-60")),
                List.of(), List.of(), List.of());

        StyleTokenDiffService.StyleTokenDiffResult result = service.diff(candidates, null);

        assertThat(result.newCandidateCount()).isEqualTo(1);
    }

    private DesignSystemProfile profileWithVariables(Map<String, VariableBinding> variables) {
        return new DesignSystemProfile(
                "ftc-krds", "FTC KRDS", "1.0.0", "registry-1", "fileKey",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), variables);
    }
}
