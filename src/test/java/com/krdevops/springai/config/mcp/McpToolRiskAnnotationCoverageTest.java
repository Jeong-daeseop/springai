package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ARCH-0102/0103 전체 커버리지 확인.
 *
 * <p>{@code com.krdevops.springai.tools} 패키지의 모든 {@code @Component} 클래스를 classpath
 * 스캔으로 찾아 그 안의 모든 {@code @Tool} 메서드가 {@link McpToolRisk}를 갖는지 직접 검증한다.
 * {@code McpConfig}의 fail-fast 검증(컨텍스트 기동 실패)과 별개로, 어떤 메서드가 빠졌는지 Spring
 * 컨텍스트 없이도 바로 알려주는 빠른 단위 테스트다. 새 Tool 클래스나 메서드가 추가돼도 이 목록을
 * 손으로 갱신할 필요가 없다(예전 {@code McpToolRiskRegistry}의 중앙 맵 방식과의 핵심 차이).
 */
class McpToolRiskAnnotationCoverageTest {

    @Test
    void everyToolMethodInToolsPackage_hasRiskAnnotation() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> missing = new ArrayList<>();
        int scannedMethods = 0;

        for (var candidate : scanner.findCandidateComponents("com.krdevops.springai.tools")) {
            Class<?> toolClass = Class.forName(candidate.getBeanClassName());
            for (Method method : toolClass.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    scannedMethods++;
                    if (!method.isAnnotationPresent(McpToolRisk.class)) {
                        missing.add(toolClass.getName() + "#" + method.getName());
                    }
                }
            }
        }

        assertThat(missing).as("@McpToolRisk 누락된 @Tool 메서드").isEmpty();
        // WP6 생성 진입점 결선 이후 실측치 — McpConfig에 새 Tool을 등록하면 이 값도 함께 갱신한다.
        // R6-046/R5-045: previewPlatformConversion·previewStyleTokenDiff 추가로 95 → 97.
        // R6-032~038(2026-08-18): generateFigmaBundleForOperation 추가로 97 → 98.
        // R6-065(2026-08-19): bindFigmaDesignRequestTable 추가로 98 → 99.
        // R8(04번 문서 §11, 2026-08-20): captureWebPageMultiViewport 추가로 99 → 100.
        // R8 Part B(04번 문서 §11, 2026-08-20): prepareFigmaBundleImport 추가로 100 → 101.
        // Region Ownership Task 8: adoptCurrentAsBaseline 추가로 101 → 102.
        // 픽셀재현 제외범위 구현계획 트랙 A: compareDesignFidelity 추가로 102 → 103.
        // 픽셀재현 제외범위 구현계획 트랙 B: downloadFigmaAssets 추가로 103 → 104.
        // 픽셀재현 2차구현 반응형검증 구현계획: checkResponsiveRegression 추가로 104 → 105.
        assertThat(scannedMethods).isEqualTo(105);
    }
}
