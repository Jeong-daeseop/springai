package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaExportBundleAssemblerV2Test {

    private final FigmaExportBundleAssembler assembler = new FigmaExportBundleAssembler();

    @Test
    void v2BundleContainsExactPatternAndPublishedRuleSetSnapshots() {
        var bundle = assembler.assemble(spec(), profile(), registry(), pattern("1.0.0"), rules("2.0.0"));

        assertThat(bundle.screenPattern().pattern()).isEqualTo(pattern("1.0.0"));
        assertThat(bundle.variantRuleSet().ruleSet()).isEqualTo(rules("2.0.0"));
        assertThat(bundle.metadata().screenPatternVersion()).isEqualTo("1.0.0");
        assertThat(bundle.metadata().variantRuleSetVersion()).isEqualTo("2.0.0");
    }

    @Test
    void mismatchedPatternVersionIsRejected() {
        assertThatThrownBy(() -> assembler.assemble(
                spec(), profile(), registry(), pattern("1.0.1"), rules("2.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUNDLE_PATTERN_VERSION_MISMATCH");
    }

    @Test
    void draftOrMismatchedRuleSetIsRejected() {
        VariantRuleSet draft = new VariantRuleSet(
                "rules", "2.0.0", "krds", "registry-1", VariantRuleSet.Status.DRAFT, rules("2.0.0").rules());

        assertThatThrownBy(() -> assembler.assemble(spec(), profile(), registry(), pattern("1.0.0"), draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUNDLE_RULE_SET_VERSION_MISMATCH");
    }

    @Test
    void draftPatternIsRejected() {
        ScreenPatternDefinition draft = new ScreenPatternDefinition(
                ScreenPattern.CRUD_LIST, "1.0.0", ScreenPatternDefinition.Status.DRAFT, List.of());

        assertThatThrownBy(() -> assembler.assemble(spec(), profile(), registry(), draft, rules("2.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUNDLE_PATTERN_NOT_PUBLISHED");
    }

    private FigmaScreenSpec spec() {
        FigmaNodeSpec content = new FigmaNodeSpec(
                "qna-list", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of());
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

    private ComponentRegistry registry() {
        return new ComponentRegistry("krds", "2.0.0", "registry-1", null, Map.of());
    }

    private ScreenPatternDefinition pattern(String version) {
        return new ScreenPatternDefinition(ScreenPattern.CRUD_LIST, version, List.of());
    }

    private VariantRuleSet rules(String version) {
        return new VariantRuleSet(
                "rules", version, "krds", "registry-1", VariantRuleSet.Status.PUBLISHED,
                List.of(new VariantRule(
                        "primary", 100, SemanticRole.ACTION_PRIMARY,
                        Map.of("pattern", "crud.list"), Map.of("type", "Primary"))));
    }
}
