package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/**
 * WP8 ARCH-0810: 현재 화면 렌더 결과를 시각 비교 baseline으로 승격해달라는 명시적 요청.
 *
 * <p>스크린샷 파일 경로를 받지 않는 것이 이 계약의 핵심 보안 속성이다 — 승격 대상은 항상 서버가
 * 이 요청을 처리하며 방금 렌더한 이미지뿐이라, 호출자가 임의 파일을 baseline으로 밀어 넣을 수 없다.
 * {@code url}과 {@code renderedHtml} 중 정확히 하나만 지정한다({@link BrowserValidationRequest} 규칙).
 */
public record BaselineApprovalRequest(
        String operationId,
        String screenId,
        String relativeFile,
        String url,
        String renderedHtml,
        List<String> viewports,
        List<String> maskSelectors,
        String readySelector
) {
    public BaselineApprovalRequest {
        viewports = viewports == null ? List.of() : List.copyOf(viewports);
        maskSelectors = maskSelectors == null ? List.of() : List.copyOf(maskSelectors);
    }
}
