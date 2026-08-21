package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.contract.PlatformLayoutPolicy;
import com.krdevops.springai.model.thymeleaf.ResponsiveBreakpointPolicy;
import com.krdevops.springai.model.thymeleaf.ViewportConstraint;
import com.krdevops.springai.service.designsystem.ComponentSwapPolicyResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R6-046: FigmaPlatformConversionService가 PlatformLayoutPolicy/ComponentSwapPolicyResolver를 실제로 소비하는지 검증. */
class FigmaPlatformConversionServiceTest {

    private final FigmaPlatformConversionService service =
            new FigmaPlatformConversionService(new ComponentSwapPolicyResolver());

    @Test
    void defaultPolicyMatchesSharedResponsiveBreakpointConstants() {
        PlatformLayoutPolicy policy = FigmaPlatformConversionService.defaultPolicy();

        PlatformLayoutPolicy.ViewportPolicy desktop = policy.getViewportFor("DESKTOP");
        assertThat(desktop.viewportWidth()).isEqualTo(ResponsiveBreakpointPolicy.DESKTOP_WIDTH);
        assertThat(desktop.gridColumns()).isEqualTo(ResponsiveBreakpointPolicy.DESKTOP_GRID_COLUMNS);
        assertThat(desktop.navigationStyle()).isEqualTo(ViewportConstraint.NavigationType.SIDE_NAV.name());

        PlatformLayoutPolicy.ViewportPolicy tablet = policy.getViewportFor("TABLET");
        assertThat(tablet.viewportWidth()).isEqualTo(ResponsiveBreakpointPolicy.TABLET_WIDTH);
        assertThat(tablet.gridColumns()).isEqualTo(ResponsiveBreakpointPolicy.TABLET_GRID_COLUMNS);
        assertThat(tablet.navigationStyle()).isEqualTo(ViewportConstraint.NavigationType.DRAWER.name());

        PlatformLayoutPolicy.ViewportPolicy mobile = policy.getViewportFor("MOBILE");
        assertThat(mobile.viewportWidth()).isEqualTo(ResponsiveBreakpointPolicy.MOBILE_WIDTH);
        assertThat(mobile.gridColumns()).isEqualTo(ResponsiveBreakpointPolicy.MOBILE_GRID_COLUMNS);
        assertThat(mobile.navigationStyle()).isEqualTo(ViewportConstraint.NavigationType.BOTTOM_NAV.name());
    }

    @Test
    void convertReturnsViewportAndUnswappedComponentsWhenNoRuleMatches() {
        FigmaPlatformConversionService.PlatformConversionResult result =
                service.convert("MOBILE", List.of("krds.table", "krds.button"));

        assertThat(result.platform()).isEqualTo("MOBILE");
        assertThat(result.viewport().viewportWidth()).isEqualTo(390);
        assertThat(result.viewport().gridColumns()).isEqualTo(4);
        assertThat(result.componentSwaps()).hasSize(2);
        assertThat(result.componentSwaps()).allSatisfy(swap -> {
            assertThat(swap.swapped()).isFalse();
            assertThat(swap.resolvedLogicalType()).isEqualTo(swap.requestedLogicalType());
        });
    }

    @Test
    void convertAppliesConfiguredComponentSwapForTargetPlatform() {
        PlatformLayoutPolicy policy = new PlatformLayoutPolicy(
                "custom-v1",
                FigmaPlatformConversionService.defaultPolicy().viewports(),
                List.of(new PlatformLayoutPolicy.ComponentSwapRule(
                        "krds.table", "krds.card-list", "MOBILE", "좁은 화면에서 표 대신 카드 목록 사용")),
                "hash");

        FigmaPlatformConversionService.PlatformConversionResult result =
                service.convert("MOBILE", List.of("krds.table"), policy);

        assertThat(result.componentSwaps()).singleElement().satisfies(swap -> {
            assertThat(swap.swapped()).isTrue();
            assertThat(swap.resolvedLogicalType()).isEqualTo("krds.card-list");
            assertThat(swap.reason()).isEqualTo("좁은 화면에서 표 대신 카드 목록 사용");
        });
    }

    /**
     * 24번 문서 §8.3: Component Swap은 서로 다른 컴포넌트로의 완전한 치환만 표현할 수 있고,
     * krds.button의 Size 같은 Variant Property 조정은 대상이 아니다. 카탈로그에 실제 스왑
     * 후보 쌍이 없으므로 approvedPolicy()는 defaultPolicy()와 마찬가지로 빈 규칙을 쓴다 —
     * viewport 정의만 공유하고 규칙은 비어 있다는 것 자체가 검증 대상이다.
     */
    @Test
    void approvedPolicyKeepsDefaultViewportsButHasNoSwapRulesYet() {
        PlatformLayoutPolicy policy = FigmaPlatformConversionService.approvedPolicy();

        assertThat(policy.viewports()).isEqualTo(FigmaPlatformConversionService.defaultPolicy().viewports());
        assertThat(policy.componentSwaps()).isEmpty();
        assertThat(policy.policyVersion()).isEqualTo("platform-layout-approved-v1");
    }

    @Test
    void recalculateGridUsesViewportPaddingAndGapDeterministically() {
        var result = service.recalculateGrid("MOBILE", FigmaPlatformConversionService.defaultPolicy());

        assertThat(result.contentWidth()).isEqualTo(358);
        assertThat(result.usableWidth()).isEqualTo(322);
        assertThat(result.columnWidth()).isEqualTo(80.5d);
    }

    @Test
    void convertRejectsUnsupportedPlatform() {
        assertThatThrownBy(() -> service.convert("WATCH", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_NOT_SUPPORTED");
    }

    @Test
    void isSupportedPlatformAcceptsOnlyTheThreeKnownPlatforms() {
        assertThat(service.isSupportedPlatform("DESKTOP")).isTrue();
        assertThat(service.isSupportedPlatform("TABLET")).isTrue();
        assertThat(service.isSupportedPlatform("MOBILE")).isTrue();
        assertThat(service.isSupportedPlatform("WATCH")).isFalse();
        assertThat(service.isSupportedPlatform(null)).isFalse();
    }
}
