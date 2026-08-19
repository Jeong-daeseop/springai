package com.krdevops.springai.model.capture;

import java.util.List;
import java.util.Map;

/**
 * R8(04번 문서 §11): Desktop/Tablet/Mobile 캡처를 하나로 묶는 경량 인덱스. 각 viewport의 실제
 * {@link RenderedDesignDocument}는 새 파일 포맷 없이 기존 {@code .figpack} Artifact로 각각
 * 독립 저장되며(기존 Release 1 저장 파이프라인 재사용), 이 Bundle은 그 artifactId 참조 +
 * 교차 viewport 분석 결과만 담는다. {@code viewportArtifacts}는 실패한 viewport를 제외한
 * 나머지만 포함한다("부분 성공 상태 처리" — 최소 1개 viewport가 성공하면 Bundle을 반환한다).
 */
public record RenderedDesignBundle(
        String schemaVersion, String bundleId, Map<String, String> viewportArtifacts,
        List<ComponentMatch> componentMatches, List<BreakpointObservation> breakpointObservations,
        List<CaptureWarning> warnings) {
    public static final String SCHEMA_VERSION = "rendered-design-bundle-v1";

    public RenderedDesignBundle {
        viewportArtifacts = viewportArtifacts == null ? Map.of() : Map.copyOf(viewportArtifacts);
        componentMatches = componentMatches == null ? List.of() : List.copyOf(componentMatches);
        breakpointObservations = breakpointObservations == null ? List.of() : List.copyOf(breakpointObservations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
