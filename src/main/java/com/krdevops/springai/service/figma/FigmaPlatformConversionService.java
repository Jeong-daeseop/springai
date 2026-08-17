package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.contract.PlatformLayoutPolicy;
import com.krdevops.springai.model.thymeleaf.ResponsiveBreakpointPolicy;
import com.krdevops.springai.model.thymeleaf.ViewportConstraint;
import com.krdevops.springai.service.designsystem.ComponentSwapPolicyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * R6-046: {@code convertPlatform}(PLATFORM_CONVERT) 요청이 실제로 소비할 Desktop/Tablet/Mobile
 * Grid·Navigation·Component Swap 정책을 계산한다. {@link PlatformLayoutPolicy}와
 * {@link ComponentSwapPolicyResolver}는 R1-015에서 이미 구현돼 있었지만 아무 곳에서도 호출되지
 * 않던 죽은 코드였다 — 이 서비스가 그 둘을 실제로 연결하는 첫 소비자다.
 *
 * <p>{@link PlatformLayoutPolicy}를 조직이 승인한 {@code DesignSystemProfile}에 저장하는 경로는
 * 아직 없으므로(R0-028 잔여 범위), 이 서비스는 {@link #defaultPolicy()}가 반환하는 초기 정책을
 * 기본값으로 사용한다. Grid 폭·열 수는 Thymeleaf 흐름과 동일한 {@link ResponsiveBreakpointPolicy}
 * 상수를 그대로 재사용해 "Figma와 Thymeleaf가 공유하는 Responsive 규칙"이라는
 * {@link PlatformLayoutPolicy} 원 설계 의도를 실제로 지킨다.
 */
@Service
@RequiredArgsConstructor
public class FigmaPlatformConversionService {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("DESKTOP", "TABLET", "MOBILE");

    private final ComponentSwapPolicyResolver swapPolicyResolver;

    public boolean isSupportedPlatform(String platform) {
        return platform != null && SUPPORTED_PLATFORMS.contains(platform);
    }

    /**
     * targetPlatform 하나에 대해 Viewport 정책과, 요청된 논리 컴포넌트 타입 각각의 Swap 결정을
     * 계산한다. Swap 규칙이 없으면 원래 타입을 그대로 유지한다(swapped=false).
     */
    public PlatformConversionResult convert(String targetPlatform, List<String> logicalTypes) {
        return convert(targetPlatform, logicalTypes, defaultPolicy());
    }

    public PlatformConversionResult convert(
            String targetPlatform, List<String> logicalTypes, PlatformLayoutPolicy policy) {
        if (!isSupportedPlatform(targetPlatform)) {
            throw new IllegalArgumentException(
                    "PLATFORM_NOT_SUPPORTED: targetPlatform은 DESKTOP, TABLET, MOBILE 중 하나여야 합니다: "
                            + targetPlatform);
        }
        PlatformLayoutPolicy.ViewportPolicy viewport = policy.getViewportFor(targetPlatform);
        if (viewport == null) {
            throw new IllegalArgumentException(
                    "PLATFORM_VIEWPORT_NOT_FOUND: 정책에 " + targetPlatform + " Viewport가 없습니다.");
        }
        List<ComponentSwapDecision> swaps = (logicalTypes == null ? List.<String>of() : logicalTypes).stream()
                .map(logicalType -> {
                    ComponentSwapPolicyResolver.Resolution resolution =
                            swapPolicyResolver.resolve(policy, targetPlatform, logicalType);
                    return new ComponentSwapDecision(
                            logicalType, resolution.resolvedLogicalType(), resolution.swapped(), resolution.reason());
                })
                .toList();
        return new PlatformConversionResult(targetPlatform, viewport, swaps, policy.policyVersion());
    }

    /**
     * Desktop 1440/12열, Tablet 768/8열, Mobile 390/4열 초기 정책. Component Swap 규칙은 비어
     * 있다 — 조직이 실제 대체 규칙을 승인하기 전까지는 어떤 컴포넌트도 임의로 바꾸지 않는다.
     * Navigation 명칭은 {@link ViewportConstraint.NavigationType}과 동일한 값을 써서 Thymeleaf
     * 흐름과 용어를 맞춘다.
     */
    public static PlatformLayoutPolicy defaultPolicy() {
        return new PlatformLayoutPolicy(
                "platform-layout-default-v1",
                List.of(
                        new PlatformLayoutPolicy.ViewportPolicy(
                                "DESKTOP", ResponsiveBreakpointPolicy.DESKTOP_WIDTH,
                                ResponsiveBreakpointPolicy.DESKTOP_GRID_COLUMNS,
                                ViewportConstraint.NavigationType.SIDE_NAV.name(), List.of()),
                        new PlatformLayoutPolicy.ViewportPolicy(
                                "TABLET", ResponsiveBreakpointPolicy.TABLET_WIDTH,
                                ResponsiveBreakpointPolicy.TABLET_GRID_COLUMNS,
                                ViewportConstraint.NavigationType.DRAWER.name(), List.of()),
                        new PlatformLayoutPolicy.ViewportPolicy(
                                "MOBILE", ResponsiveBreakpointPolicy.MOBILE_WIDTH,
                                ResponsiveBreakpointPolicy.MOBILE_GRID_COLUMNS,
                                ViewportConstraint.NavigationType.BOTTOM_NAV.name(), List.of())
                ),
                List.of(),
                "sha256:platform-layout-default-v1-no-swaps");
    }

    public record ComponentSwapDecision(
            String requestedLogicalType, String resolvedLogicalType, boolean swapped, String reason) {
    }

    public record PlatformConversionResult(
            String platform,
            PlatformLayoutPolicy.ViewportPolicy viewport,
            List<ComponentSwapDecision> componentSwaps,
            String policyVersion) {
    }
}
